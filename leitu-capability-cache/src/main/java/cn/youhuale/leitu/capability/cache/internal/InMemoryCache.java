package cn.youhuale.leitu.capability.cache.internal;

import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 进程内默认实现——<b>只负责存储</b>：TTL 惰性过期 + LRU 上限 + 租户作用域键。
 * 外部经 {@code Caches.inMemory(...)} 获取，不直接实例化。
 *
 * <h3>角色边界（本类只占第一个）</h3>
 * <ol>
 *   <li><b>存储</b>＝本类：查表、回写、过期、淘汰。<b>不调用装载器，不持有跨用户代码的锁。</b></li>
 *   <li><b>装载编排</b>＝{@link LoadLedger}：同键只装载一次、在途凭证、环侦测。</li>
 *   <li><b>装载器</b>＝调用方传入的 {@code Function}：生产值，可自由读库 / 调下游 / 读别的缓存。</li>
 * </ol>
 *
 * <p>为什么必须拆开：编排若用「持有一把锁」表达，锁就必然横跨装载器执行的整段时间，
 * 装载器因此被夹进存储的临界区——一次慢装载冻结整个缓存，两个缓存互相装载则锁序成环。
 * 那是角色没拆、不是锁太粗：存储不该调用用户代码。
 */
public final class InMemoryCache<K, V> implements Cache<K, V> {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private record CachedEntry<V>(V value, Instant expiresAt) {
    }

    private final long maxSize;

    private final ExecutionContextReader reader;

    private final Clock clock;

    /** 环路径里指认"哪台缓存"用的标签（跨实例可区分）。 */
    private final String cacheLabel = "Cache#" + SEQ.incrementAndGet();

    private final LoadLedger<V> ledger = new LoadLedger<>(cacheLabel);

    private final LinkedHashMap<ScopedKey, CachedEntry<V>> map = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ScopedKey, CachedEntry<V>> eldest) {
            return size() > maxSize;
        }
    };

    public InMemoryCache(long maxSize, ExecutionContextReader reader, Clock clock) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize 必须为正（如 1000）：无上限缓存是内存 footgun。当前：" + maxSize);
        }
        this.maxSize = maxSize;
        this.reader = Objects.requireNonNull(reader,
                "reader 必填：租户作用域从执行上下文来——传 ExecutionContextReader.threadLocal()");
        this.clock = Objects.requireNonNull(clock, "clock 必填：过期时刻从缓存时钟来——测试传可拨 Clock");
    }

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key 必填：缺失语义是 Optional.empty()，不是 null 入参");
        synchronized (map) {
            CachedEntry<V> entry = map.get(scoped(key));
            if (entry == null) {
                return Optional.empty();
            }
            if (expired(entry)) {
                map.remove(scoped(key));    // 读时惰性清除
                return Optional.empty();
            }
            return Optional.of(entry.value());
        }
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        Objects.requireNonNull(key, "key 必填：无键无法定位缓存条目");
        Objects.requireNonNull(value, "value 必填：缓存不存 null 值——\"无值\"语义请不缓存（每次装载）");
        Cache.requirePositiveTtl(ttl);
        synchronized (map) {
            map.put(scoped(key), new CachedEntry(value, clock.instant().plus(ttl)));
        }
    }

    @Override
    public boolean evict(K key) {
        Objects.requireNonNull(key, "key 必填：未见语义是 false，不是 null 入参");
        synchronized (map) {
            return map.remove(scoped(key)) != null;
        }
    }

    /**
     * 装载三段式：命中即返；未命中交给 {@link LoadLedger} 编排——同键只有一个线程真正装载，其余等在途凭证上。
     *
     * <p><b>装载器不在任何缓存锁内执行</b>：锁只保护「查表 / 回写」这一瞬，永不跨过用户代码。
     * 因此装载期本缓存的读、写、淘汰全部照常，装载器读别的缓存也不构成锁序环。
     *
     * <p>装载失败不缓存不吞——异常原样上抛（跟随者拿到的是同一个异常实例）。
     * 装载器返回 null 是编程错误；装载图成环不是挂死而是 {@code CyclicCacheLoadException}（见 {@link LoadLedger}）。
     */
    @Override
    public V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        Objects.requireNonNull(key, "key 必填：缺失语义是 Optional.empty()，不是 null 入参");
        Objects.requireNonNull(loader, "loader 必填：只读不装就改用 get(...)");
        Cache.requirePositiveTtl(ttl);
        ScopedKey scoped = scoped(key);
        return ledger.load(scoped,
                () -> peek(scoped),
                () -> Objects.requireNonNull(loader.apply(key),
                        "装载器不能返回 null：缓存不存 null 值——\"无值\"语义请不缓存（每次装载）"),
                loaded -> {
                    synchronized (map) {
                        map.put(scoped, new CachedEntry(loaded, clock.instant().plus(ttl)));
                    }
                });
    }

    /** 命中返回值，未命中或已过期返回 null（过期条目惰性清除）。只在 map 锁内跑，不调用用户代码。 */
    private V peek(ScopedKey scoped) {
        synchronized (map) {
            CachedEntry<V> entry = map.get(scoped);
            if (entry == null) {
                return null;
            }
            if (expired(entry)) {
                map.remove(scoped);
                return null;
            }
            return entry.value();
        }
    }

    private ScopedKey scoped(K key) {
        return new ScopedKey(reader.current().operator().tenant(), key);
    }

    private boolean expired(CachedEntry<V> entry) {
        return !clock.instant().isBefore(entry.expiresAt());
    }

    /** 大声标注：进程内无序列化，分布式替换走 adapter（分维度默认值：缓存给最小可用实现）。 */
    @Override
    public String toString() {
        return "Cache{内存实现，LRU 上限 " + maxSize + "，进程内无序列化——分布式替换走 adapter（见 docs/problems/cache.md）}";
    }
}
