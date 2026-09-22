package cn.youhuale.leitu.capability.cache;

import cn.youhuale.leitu.capability.cache.api.Caches;
import cn.youhuale.leitu.capability.cache.api.CyclicCacheLoadException;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** cache 能力的单测：锁 TTL/LRU/装载原子/租户作用域与 Noop 语义。 */
class CacheTest {

    /** 可拨时钟：TTL 过期可断言。 */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-13T00:00:00Z");

        void tick(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static final cn.youhuale.leitu.core.context.spi.ExecutionContextBinder BINDER =
            ExecutionContextBinders.threadLocal();

    private final MutableClock clock = new MutableClock();

    private Cache<String, String> cache(long maxSize) {
        return Caches.inMemory(maxSize, clock);
    }

    @Test
    void 命中与未命中_Optional语义() {
        Cache<String, String> cache = cache(10);
        assertTrue(cache.get("k").isEmpty());
        cache.put("k", "v", Duration.ofSeconds(30));
        assertEquals(Optional.of("v"), cache.get("k"));
    }

    @Test
    void TTL到期_读时惰性清除按未命中() {
        Cache<String, String> cache = cache(10);
        cache.put("k", "v", Duration.ofSeconds(30));
        assertEquals(Optional.of("v"), cache.get("k"));
        clock.tick(Duration.ofSeconds(31));      // 越过 expiresAt（避开等值边界）
        assertTrue(cache.get("k").isEmpty(), "过期即 miss（读时惰性清除，无后台线程）");
    }

    @Test
    void TTL非正即编程错误_异常即教程() {
        Cache<String, String> cache = cache(10);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> cache.put("k", "v", Duration.ZERO));
        assertTrue(e.getMessage().contains("TTL 必须为正"), "异常必须即教程：" + e.getMessage());
        assertTrue(e.getMessage().contains("PT30S"), "必须给示例：" + e.getMessage());
    }

    @Test
    void LRU上限_最久未用先驱逐() {
        Cache<String, String> cache = cache(2);
        cache.put("a", "1", Duration.ofMinutes(10));
        cache.put("b", "2", Duration.ofMinutes(10));
        cache.get("a");                           // 访问序：a 最近被用
        cache.put("c", "3", Duration.ofMinutes(10));   // 超上限，最久未用的 b 被驱逐
        assertTrue(cache.get("b").isEmpty(), "最久未用先驱逐");
        assertEquals(Optional.of("1"), cache.get("a"));
        assertEquals(Optional.of("3"), cache.get("c"));
    }

    @Test
    void 容量上限驱逐_跨租户合计() {
        Cache<String, String> cache = cache(2);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("a", "tenant-a"), "t-1"))) {
            cache.put("k1", "1", Duration.ofMinutes(10));
            cache.put("k2", "2", Duration.ofMinutes(10));
        }
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("b", "tenant-b"), "t-2"))) {
            cache.put("k3", "3", Duration.ofMinutes(10));   // 全局最久未用（tenant-a 的 k1）被驱逐——上限是实例级
        }
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("a", "tenant-a"), "t-1"))) {
            assertTrue(cache.get("k1").isEmpty(), "maxSize 跨租户合计（边界明示）");
            assertEquals(Optional.of("2"), cache.get("k2"));
        }
    }

    @Test
    void 装载三段式_同键只装载一次() {
        Cache<String, String> cache = cache(10);
        AtomicInteger loads = new AtomicInteger();
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.getOrLoad("k", k -> "v" + loads.incrementAndGet(), Duration.ofMinutes(10));
            cache.getOrLoad("k", k -> "v" + loads.incrementAndGet(), Duration.ofMinutes(10));
            assertEquals(1, loads.get(), "第二次命中不再装载");
        }
    }

    @Test
    void 并发装载_两线程同键装载器只跑一次() throws InterruptedException {
        Cache<String, String> cache = cache(10);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    try (var threadScope = BINDER.bind(
                            ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
                        cache.getOrLoad("k", k -> {
                            loads.incrementAndGet();
                            try {
                                Thread.sleep(50);    // 拉长装载窗口，制造竞争
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return "v";
                        }, Duration.ofMinutes(10));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(1, loads.get(), "同键并发只装载一次（同键落同一分片，串行保证）");
    }

    @Test
    void 观察装饰下并发装载_仍只一次() throws InterruptedException {
        Cache<String, String> cache = Caches.observing(cache(10), event -> {
        });
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    try (var threadScope = BINDER.bind(
                            ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
                        cache.getOrLoad("k", k -> {
                            loads.incrementAndGet();
                            return "v";
                        }, Duration.ofMinutes(10));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            thread.start();
            threads.add(thread);
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(1, loads.get(), "装饰器覆写 getOrLoad——装载编排留在 delegate，装饰不击穿");
    }

    /**
     * 反向自测：装载器卡住时，同一缓存的其他键仍可读写淘汰。
     *
     * <p>若有人把装载器挪回 map 锁内（回到 {@code synchronized (map) { loader.apply(...) }}），
     * 一次慢装载就阻塞本缓存所有键的一切操作——下面的 put/get/evict 会一起卡死，
     * 主线程拿不到锁，测试超时变红。
     *
     * <p>挂 {@code @Timeout} 而不是靠"跑很久没结束"来发现：卡死要能变成红灯，而不是变成一次慢构建。
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void 慢装载不阻塞其他键的读写淘汰() throws InterruptedException {
        Cache<String, String> cache = cache(10);
        CountDownLatch inLoader = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread slow = new Thread(() -> {
            try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
                cache.getOrLoad("slow", k -> {
                    inLoader.countDown();
                    try {
                        // 有界等待：即便主线程被卡住（旧实现），装载线程也会自己退出，
                        // 让"卡死"表现为一次红灯而不是一次永远不结束的构建
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "v-slow";
                }, Duration.ofMinutes(10));
            }
        });
        slow.setDaemon(true);
        slow.start();
        assertTrue(inLoader.await(2, TimeUnit.SECONDS), "装载器已进入（否则测不到阻塞）");
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-2"))) {
            cache.put("other", "v-other", Duration.ofMinutes(10));
            assertEquals(Optional.of("v-other"), cache.get("other"), "装载期他键可读");
            assertTrue(cache.evict("other"), "装载期他键可淘汰");
        }
        release.countDown();
        slow.join(5000);
        assertFalse(slow.isAlive(), "放行后装载线程应正常结束");
    }

    /**
     * 装载图成环（A 的装载器读 B、B 又读回 A 同一个键）——值依赖它自己，无解。
     *
     * <p>框架的态度：这不是"慢"，是<b>没有答案</b>，所以不等待、不超时、不静默重试，
     * 而是把环路径报出来。若退回"装载器持锁运行"的旧结构，这里会变成无声挂死——
     * 挂 {@code @Timeout} 是为了让那种退化变成红灯，而不是变成一次永远不结束的构建。
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void 装载图成环_大声报出环路径而不是挂死() {
        Cache<String, String> users = cache(10);
        Cache<String, String> orders = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            CyclicCacheLoadException e = assertThrows(CyclicCacheLoadException.class, () ->
                    users.getOrLoad("u-1",
                            k -> orders.getOrLoad("o-1",
                                    k2 -> users.getOrLoad("u-1", k3 -> "永远算不出来", Duration.ofMinutes(10)),
                                    Duration.ofMinutes(10)),
                            Duration.ofMinutes(10)));
            assertTrue(e.cyclePath().contains("u-1"), "环路径要指认是哪个键：" + e.cyclePath());
            assertTrue(e.getMessage().contains("无解"), "异常必须即教程：" + e.getMessage());
            assertTrue(e.getMessage().contains("装载器"), "责任方要说清（在装载器里，不在缓存里）");
        }
    }

    /**
     * 双线程版的环：两台缓存各由一个线程装载，装载器互相读对方——经典的"锁序死锁"形态。
     *
     * <p>登记等待边与判环在同一把锁内完成，所以先到的一方判不出、后到的一方必判出；
     * 两侧都不会无声挂死。这是环检测真正要保证的场景（单线程那条只覆盖自环）。
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void 两线程互等_环检测解开_不挂死() throws InterruptedException {
        Cache<String, String> users = cache(10);
        Cache<String, String> orders = cache(10);
        CountDownLatch inUsers = new CountDownLatch(1);
        CountDownLatch inOrders = new CountDownLatch(1);
        AtomicReference<Throwable> errUsers = new AtomicReference<>();
        AtomicReference<Throwable> errOrders = new AtomicReference<>();

        Thread tu = new Thread(() -> {
            try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
                users.getOrLoad("k", k -> {
                    inUsers.countDown();
                    awaitQuietly(inOrders);              // 两边都进了装载器，才构成互等
                    return orders.getOrLoad("k", k2 -> "v-orders", Duration.ofMinutes(10));
                }, Duration.ofMinutes(10));
            } catch (Throwable t) {
                errUsers.set(t);
            }
        });
        Thread to = new Thread(() -> {
            try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-2"))) {
                orders.getOrLoad("k", k -> {
                    inOrders.countDown();
                    awaitQuietly(inUsers);
                    return users.getOrLoad("k", k2 -> "v-users", Duration.ofMinutes(10));
                }, Duration.ofMinutes(10));
            } catch (Throwable t) {
                errOrders.set(t);
            }
        });
        tu.setDaemon(true);
        to.setDaemon(true);
        tu.start();
        to.start();
        // 有界 join（两侧各 3s < @Timeout 10s）：真挂死时先由这条断言报红并给出可读理由，
        // 而不是等到超时——红灯要指认死因，不只是"跑太久"
        tu.join(3000);
        to.join(3000);
        assertFalse(tu.isAlive() || to.isAlive(), "成环不允许无声挂死：两个线程都必须结束");
        Throwable failure = errUsers.get() != null ? errUsers.get() : errOrders.get();
        assertTrue(failure instanceof CyclicCacheLoadException,
                "至少一个线程必须报出环（另一个拿到同一异常）：" + failure);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 装载器抛异常：锁必须释放（finally），否则同键从此永久卡死。 */
    @Test
    void 装载器抛异常_锁释放_同键可再装载() {
        Cache<String, String> cache = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertThrows(IllegalStateException.class, () -> cache.getOrLoad("k", k -> {
                throw new IllegalStateException("装载失败");
            }, Duration.ofMinutes(10)));
            assertEquals("v", cache.getOrLoad("k", k -> "v", Duration.ofMinutes(10)),
                    "异常后锁已释放，同键可再次装载");
        }
    }

    @Test
    void 装载器返回null即编程错误() {
        Cache<String, String> cache = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            NullPointerException e = assertThrows(NullPointerException.class,
                    () -> cache.getOrLoad("k", k -> null, Duration.ofMinutes(10)));
            assertTrue(e.getMessage().contains("不存 null"));
        }
    }

    @Test
    void 租户隔离_他租户同键不可见() {
        Cache<String, String> cache = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.put("k", "a-value", Duration.ofMinutes(10));
        }
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("mallory", "tenant-b"), "t-2"))) {
            assertTrue(cache.get("k").isEmpty(), "他租户同键不可见");
        }
    }

    @Test
    void 系统作用域_横杠不是通配() {
        Cache<String, String> cache = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.system(), "t-sys"))) {
            cache.put("ref", "system-value", Duration.ofMinutes(10));
        }
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertTrue(cache.get("ref").isEmpty(), "系统缓存不对租户可见——各租户各载一份（安全重复）");
        }
    }

    @Test
    void evict作用域内命中true_再见false() {
        Cache<String, String> cache = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.put("k", "v", Duration.ofMinutes(10));
            assertTrue(cache.evict("k"));
            assertFalse(cache.evict("k"));
        }
    }

    @Test
    void Noop_零开销禁用_装载每次发生且put不缓存() {
        Cache<String, String> cache = Caches.noop();
        AtomicInteger loads = new AtomicInteger();
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.put("k", "v", Duration.ofMinutes(10));
            assertTrue(cache.get("k").isEmpty(), "Noop 不缓存");
            assertEquals("v1", cache.getOrLoad("k", k -> "v" + loads.incrementAndGet(), Duration.ofMinutes(10)));
            assertEquals("v2", cache.getOrLoad("k", k -> "v" + loads.incrementAndGet(), Duration.ofMinutes(10)));
            assertEquals(2, loads.get(), "Noop 每次装载");
        }
    }

    @Test
    void CachePolicy来自配置_缺省与自定义与教学异常() {
        // 缺省：空配置 → 1000 / PT30M / true
        CachePolicy defaults = CachePolicy.fromConfig(ConfigReader.of());
        assertEquals(1000, defaults.maxSize());
        assertEquals(Duration.ofMinutes(30), defaults.ttl());
        assertTrue(defaults.enabled());
        // 自定义
        ConfigReader config = ConfigReader.of(ConfigSources.fromMap("缓存配置", Map.of(
                "cache.max-size", "2",
                "cache.ttl", "PT1M",
                "cache.enabled", "false")));
        CachePolicy custom = CachePolicy.fromConfig(config);
        assertEquals(2, custom.maxSize());
        assertEquals(Duration.ofMinutes(1), custom.ttl());
        assertFalse(custom.enabled());
        // 有值但解析失败 = ConfigReader 教学异常（不含值）
        ConfigReader bad = ConfigReader.of(ConfigSources.fromMap("坏配置", Map.of("cache.ttl", "30s")));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CachePolicy.fromConfig(bad));
        assertTrue(e.getMessage().contains("cache.ttl"));
        assertTrue(e.getMessage().contains("PT30S"));
    }

    @Test
    void maxSize非正即编程错误() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Caches.inMemory(0));
        assertTrue(e.getMessage().contains("footgun"), "异常必须即教程：" + e.getMessage());
    }

    @Test
    void null入参必填教学() {
        Cache<String, String> cache = cache(10);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            NullPointerException e1 = assertThrows(NullPointerException.class, () -> cache.get(null));
            assertTrue(e1.getMessage().contains("必填"));
            NullPointerException e2 = assertThrows(NullPointerException.class,
                    () -> cache.put("k", null, Duration.ofMinutes(10)));
            assertTrue(e2.getMessage().contains("不存 null"));
            NullPointerException e3 = assertThrows(NullPointerException.class, () -> cache.evict(null));
            assertTrue(e3.getMessage().contains("必填"));
        }
    }

    @Test
    void 观察装饰记事件_带锚点与键() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = events::add;
        Cache<String, String> cache = Caches.observing(cache(10), recorder);
        try (var s = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-9"))) {
            cache.put("k", "v", Duration.ofMinutes(10));
            cache.get("k");
            cache.get("no-such");
            cache.evict("k");
        }
        assertEquals(4, events.size());
        assertEquals("cache.put", events.get(0).name());
        assertEquals("cache.hit", events.get(1).name());
        assertEquals("cache.miss", events.get(2).name());
        assertEquals("cache.evict.hit", events.get(3).name());
        assertEquals("k", events.get(0).attributes().get("cache.key"));
        assertEquals("alice", events.get(0).context().operator().subject());
        assertEquals("trace-9", events.get(0).context().traceId());
    }

    @Test
    void 内存实现与Noop的toString大声标注() {
        String inMemory = cache(10).toString();
        assertTrue(inMemory.contains("LRU") && inMemory.contains("adapter"), "边界自我声明：" + inMemory);
        String noop = Caches.noop().toString();
        assertTrue(noop.contains("Noop") && noop.contains("无害维度"), "Noop 自我声明：" + noop);
    }
}
