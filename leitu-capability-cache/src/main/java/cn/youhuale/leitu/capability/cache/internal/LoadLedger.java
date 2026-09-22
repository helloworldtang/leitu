package cn.youhuale.leitu.capability.cache.internal;

import cn.youhuale.leitu.capability.cache.api.CyclicCacheLoadException;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 装载编排——与「存储」分开的第二个角色（第三个是装载器本身，属调用方）。
 *
 * <h3>三个角色各自的边界</h3>
 * <ol>
 *   <li><b>存储</b>（{@link InMemoryCache}）：只管查表、回写、过期与淘汰，<b>不认识装载器</b>。</li>
 *   <li><b>编排</b>（本类）：只管「同一个键此刻有没有人在算」，<b>不生产值</b>。</li>
 *   <li><b>装载器</b>（调用方）：生产值，<b>不受任何缓存锁约束</b>，可以自由读库、调下游、读别的缓存。</li>
 * </ol>
 *
 * <p>把它们分开之前，编排是靠「持有一把锁」来表达的，而锁必须横跨装载器执行的整段时间，
 * 于是装载器被夹进存储的临界区——一次慢装载冻结整个缓存，两个缓存互相装载则锁序成环。
 * 那不是锁粒度问题，是<b>角色没拆开</b>：存储不该调用用户代码。
 *
 * <h3>现在的机制</h3>
 * <p>编排改用「在途凭证」表达：第一个到的线程登记凭证并成为<b>领导者</b>，
 * 在<b>不持有任何锁</b>的情况下执行装载器；其余线程成为<b>跟随者</b>，等在同一张凭证上。
 * 锁只保护「登记 / 回写」这一瞬，永不跨过用户代码。
 *
 * <p>由此得到两个结果：① 装载期不再阻塞本缓存任何操作（读写淘汰照常）；
 * ② 装载器读别的缓存不再构成锁序环——因为等待的是凭证，不是锁。
 *
 * <h3>剩下的环怎么处置</h3>
 * <p>若装载图本身成环（A 的值依赖 B，B 的值又依赖 A），那是求值图无解，
 * 等价于一段永不终止的递归——锁拆得再细也救不了。此处不回避：<b>等待前先查等待图</b>，
 * 沿「凭证 → 持有者线程 → 该线程正在等的凭证」走一圈，走回自己即成环，
 * 立刻抛 {@link CyclicCacheLoadException} 并带上环路径；
 * 让故障表现为一条可读的报错，而不是一次无声的挂死。
 */
final class LoadLedger<V> {

    /**
     * 全局等待图：线程 → 它此刻正在等待的那张在途凭证。
     *
     * <p>为什么是静态（跨缓存实例共享）：环跨越两台缓存（A 的装载器读 B），
     * 每台各存一半就看不全这条边——等待图必须是进程内唯一的一张。
     * 条目在等待结束时于 finally 中移除，不留泄漏。
     */
    private static final Map<Thread, InFlight<?>> WAITING = new ConcurrentHashMap<>();

    /** 等待图的互斥锁：只在"登记 + 判环"这一小段持有，期间不跑用户代码、不阻塞装载。 */
    private static final Object GRAPH = new Object();

    private final ConcurrentHashMap<ScopedKey, InFlight<V>> inFlight = new ConcurrentHashMap<>();

    private final String cacheLabel;

    LoadLedger(String cacheLabel) {
        this.cacheLabel = Objects.requireNonNull(cacheLabel, "cacheLabel 必填：环路径要能指认是哪台缓存");
    }

    /**
     * 编排一次装载。
     *
     * @param key       装载键（已复合租户）
     * @param cached    复查钩子：返回当前已缓存的值（未命中返回 null）——消除「peek 未命中→登记」之间的竞态
     * @param loader    值生产（只在领导者线程上、且不在任何锁内执行）
     * @param onLoaded  回写钩子（领导者装载成功后写回存储）
     */
    V load(ScopedKey key, Supplier<V> cached, Supplier<V> loader, Consumer<V> onLoaded) {
        InFlight<V> present = inFlight.get(key);
        if (present == null) {
            InFlight<V> mine = new InFlight<>(label(key));
            present = inFlight.putIfAbsent(key, mine);
            if (present == null) {
                return lead(key, mine, cached, loader, onLoaded);
            }
        }
        return follow(present);
    }

    /** 领导者：登记后复查一次，仍缺失才真正装载；装载器全程不持锁。 */
    private V lead(ScopedKey key, InFlight<V> mine, Supplier<V> cached,
                   Supplier<V> loader, Consumer<V> onLoaded) {
        try {
            V already = cached.get();                 // 登记与 peek 之间可能已被他线程写回
            if (already != null) {
                return already;
            }
            V value = loader.get();
            mine.value = value;
            onLoaded.accept(value);
            return value;
        } catch (RuntimeException | Error e) {
            mine.failure = e;                         // 原样传给跟随者：不吞、不换、不包装
            throw e;
        } finally {
            inFlight.remove(key);
            mine.done.countDown();
        }
    }

    /** 跟随者：等同一张凭证。等待是唯一可能成环的地方，所以先登记等待边再判环。 */
    private V follow(InFlight<V> target) {
        Thread me = Thread.currentThread();
        // 登记与判环必须在同一个锁内完成：否则两侧可能各自"读时对方还没登记"而双双放行，环照旧挂死。
        synchronized (GRAPH) {
            WAITING.put(me, target);
            try {
                detectCycle(target, me);
            } catch (RuntimeException | Error e) {
                WAITING.remove(me);
                throw e;
            }
        }
        try {
            target.done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("装载等待被中断：[" + target.label()
                    + "] 仍由线程「" + target.owner().getName() + "」装载中——"
                    + "中断不改变装载结果，可重试本次 getOrLoad");
        } finally {
            synchronized (GRAPH) {
                WAITING.remove(me);
            }
        }
        Throwable failure = target.failure;
        if (failure != null) {
            if (failure instanceof RuntimeException re) {
                throw re;
            }
            if (failure instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException("装载失败", failure);
        }
        return target.value;
    }

    /**
     * 沿等待图走一圈：凭证 → 持有者线程 → 该线程正在等的凭证 → …
     * 走回自己即成环。走不通（对方正在跑装载器、尚未等待）即此刻无环，正常等待。
     *
     * <p>调用方必须持有 {@link #GRAPH}——判环读的是整张图，半张图判不出环。
     */
    private void detectCycle(InFlight<?> target, Thread me) {
        StringBuilder path = new StringBuilder(target.label());
        Set<Thread> seen = new HashSet<>();
        InFlight<?> cur = target;
        while (cur != null) {
            if (cur.owner() == me) {
                throw new CyclicCacheLoadException(path + " → " + target.label());
            }
            if (!seen.add(cur.owner())) {
                return;                               // 图正在变动：降级为等待，不误报
            }
            InFlight<?> next = WAITING.get(cur.owner());
            if (next == null) {
                return;                               // 持有者正在跑装载器，未处于等待态
            }
            path.append(" → ").append(next.label());
            cur = next;
        }
    }

    private String label(ScopedKey key) {
        return cacheLabel + "{" + key.tenant() + " / " + key.key() + "}";
    }

    /** 一张在途凭证：同键并发的汇聚点。跟随者等它，领导者填它。 */
    private static final class InFlight<V> {

        private final String label;

        private final Thread owner = Thread.currentThread();

        private final CountDownLatch done = new CountDownLatch(1);

        private volatile V value;

        private volatile Throwable failure;

        InFlight(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        Thread owner() {
            return owner;
        }
    }
}
