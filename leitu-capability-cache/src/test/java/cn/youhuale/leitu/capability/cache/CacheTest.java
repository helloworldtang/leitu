package cn.youhuale.leitu.capability.cache;

import cn.youhuale.leitu.capability.cache.api.Caches;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

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
        assertEquals(1, loads.get(), "同键并发只装载一次（粗粒度互斥保证）");
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
        assertEquals(1, loads.get(), "装饰器覆写 getOrLoad——原子装载留在 delegate 锁内，装饰不击穿");
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
