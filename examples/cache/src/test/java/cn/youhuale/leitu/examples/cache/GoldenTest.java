package cn.youhuale.leitu.examples.cache;

import cn.youhuale.leitu.capability.cache.api.Caches;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定缓存的标准用法语义。capability 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    private static final cn.youhuale.leitu.core.context.spi.ExecutionContextBinder BINDER =
            ExecutionContextBinders.threadLocal();

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

    @Test
    void 存取与未命中_put后命中_无键为空() {
        Cache<String, String> cache = Caches.inMemory(10);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertTrue(cache.get("k").isEmpty());
            cache.put("k", "v", Duration.ofMinutes(10));
            assertEquals(Optional.of("v"), cache.get("k"));
        }
    }

    @Test
    void TTL过期_时钟可拨() {
        MutableClock clock = new MutableClock();
        Cache<String, String> cache = Caches.inMemory(10, clock);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.put("k", "v", Duration.ofMinutes(10));
            clock.tick(Duration.ofMinutes(11));
            assertTrue(cache.get("k").isEmpty(), "越过 expiresAt 即 miss");
        }
    }

    @Test
    void LRU上限_最久未用先被驱逐() {
        Cache<String, String> cache = Caches.inMemory(2);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.put("a", "1", Duration.ofMinutes(10));
            cache.put("b", "2", Duration.ofMinutes(10));
            cache.get("a");
            cache.put("c", "3", Duration.ofMinutes(10));
            assertTrue(cache.get("b").isEmpty(), "最久未用先被驱逐");
            assertEquals(Optional.of("1"), cache.get("a"));
        }
    }

    @Test
    void 装载三段式_第二次命中不再装载() {
        Cache<String, String> cache = Caches.inMemory(10);
        AtomicInteger loads = new AtomicInteger();
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertEquals("v1", cache.getOrLoad("k", k -> "v" + loads.incrementAndGet(), Duration.ofMinutes(10)));
            assertEquals("v1", cache.getOrLoad("k", k -> "v" + loads.incrementAndGet(), Duration.ofMinutes(10)));
            assertEquals(1, loads.get());
        }
    }

    @Test
    void 租户隔离_他租户同键不可见() {
        Cache<String, String> cache = Caches.inMemory(10);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            cache.put("k", "a-value", Duration.ofMinutes(10));
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("mallory", "tenant-b"), "t-2"))) {
            assertTrue(cache.get("k").isEmpty());
        }
    }

    @Test
    void 配置驱动装配_CachePolicy来自配置源() {
        ConfigReader config = ConfigReader.of(ConfigSources.fromMap("缓存配置", Map.of(
                "cache.max-size", "2", "cache.ttl", "PT5M", "cache.enabled", "true")));
        CachePolicy policy = CachePolicy.fromConfig(config);
        assertEquals(2, policy.maxSize());
        assertEquals(Duration.ofMinutes(5), policy.ttl());
        assertTrue(policy.enabled());

        // 装配三分支在装配处可见；禁用分支 → Noop
        Cache<String, String> assembled = policy.enabled()
                ? Caches.inMemory(policy.maxSize())
                : Caches.noop();
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertEquals("v", assembled.getOrLoad("k", k -> "v", policy.ttl()));
        }
        CachePolicy disabled = CachePolicy.fromConfig(
                ConfigReader.of(ConfigSources.fromMap("禁用", Map.of("cache.enabled", "false"))));
        assertFalse(disabled.enabled());
        assertTrue(Caches.noop().get("k").isEmpty());
    }

    @Test
    void 观察装饰_cache事件带锚点与键() {
        List<ObservationEvent> events = new ArrayList<>();
        Cache<String, String> cache = Caches.observing(Caches.inMemory(10), events::add);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-9"))) {
            cache.put("k", "v", Duration.ofMinutes(10));
            cache.get("k");
            cache.getOrLoad("k2", k -> "v2", Duration.ofMinutes(10));
        }
        assertEquals("cache.put", events.get(0).name());
        assertEquals("cache.hit", events.get(1).name());
        assertEquals("cache.miss", events.get(2).name(), "k2 未命中");
        assertEquals("cache.put", events.get(3).name(), "装载后记 put（duration≈装载耗时）");
        assertEquals("k", events.get(0).attributes().get("cache.key"));
        assertEquals("alice", events.get(0).context().operator().subject());
        assertEquals("trace-9", events.get(0).context().traceId());
    }

    @Test
    void 兜底与禁用大声标注() {
        String inMemory = Caches.inMemory(100).toString();
        assertTrue(inMemory.contains("LRU") && inMemory.contains("adapter"), "边界自我声明：" + inMemory);
        String noop = Caches.noop().toString();
        assertTrue(noop.contains("Noop"), "禁用自我声明：" + noop);
    }
}
