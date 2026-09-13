package cn.youhuale.leitu.examples.cache;

import cn.youhuale.leitu.capability.cache.api.Caches;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 金样本：缓存的最小可运行示范。
 *
 * <p>照着这个样子用缓存——这是"怎么缓存"的标准答案用法
 * （docs/problems/cache.md；消费 config-source 与 what-happened）。
 */
public final class Demo {

    /** 可拨时钟：TTL 过期可演示。 */
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

    public static void main(String[] args) {
        var binder = ExecutionContextBinders.threadLocal();
        MutableClock clock = new MutableClock();
        ObservationRecorder recorder = ObservationRecorder.of(event -> System.out.println("观测:     " + event));

        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-100"))) {

            // 1) 命中与未命中：put 后命中，无键为空
            Cache<String, String> cache = Caches.observing(Caches.inMemory(3, clock), recorder);
            cache.put("user:1", "alice", Duration.ofMinutes(10));
            System.out.println("命中:     " + cache.get("user:1"));
            System.out.println("未命中:   " + cache.get("user:2"));

            // 2) TTL 过期：时钟拨过 expiresAt，读时惰性清除
            clock.tick(Duration.ofMinutes(11));
            System.out.println("过期后:   " + cache.get("user:1") + "（读时惰性清除，无后台线程）");

            // 3) LRU 上限：maxSize=3，访问 a 后插入 d——最久未用的 b 被驱逐
            clock.tick(Duration.ofMinutes(-11));
            Cache<String, String> lru = Caches.inMemory(3, clock);
            lru.put("a", "1", Duration.ofMinutes(10));
            lru.put("b", "2", Duration.ofMinutes(10));
            lru.put("c", "3", Duration.ofMinutes(10));
            lru.get("a");
            lru.put("d", "4", Duration.ofMinutes(10));
            System.out.println("LRU:      b=" + lru.get("b") + " a=" + lru.get("a") + "（b 最久未用被驱逐）");

            // 4) 装载三段式：同键只装载一次
            AtomicInteger loads = new AtomicInteger();
            Cache<String, String> loader = Caches.inMemory(10, clock);
            System.out.println("装载:     " + loader.getOrLoad("dict", k -> "值" + loads.incrementAndGet(), Duration.ofMinutes(10)));
            System.out.println("再装载:   " + loader.getOrLoad("dict", k -> "值" + loads.incrementAndGet(), Duration.ofMinutes(10))
                    + "（装载器只跑了 " + loads.get() + " 次）");

            // 5) 租户隔离：他租户同键不可见
            Cache<String, String> scoped = Caches.inMemory(10, clock);
            scoped.put("ref", "tenant-a 的值", Duration.ofMinutes(10));
            try (var inner = binder.bind(ExecutionContext.of(Operator.human("mallory", "tenant-b"), "trace-101"))) {
                System.out.println("隔离:     tenant-b 读同键=" + scoped.get("ref"));
            }

            // 6) 配置驱动装配：CachePolicy 来自配置源（enabled=false → Noop）
            ConfigReader config = ConfigReader.of(ConfigSources.fromMap("缓存配置", Map.of(
                    "cache.max-size", "2", "cache.ttl", "PT5M", "cache.enabled", "true")));
            CachePolicy policy = CachePolicy.fromConfig(config);
            System.out.println("策略:     " + policy);
            Cache<String, String> assembled = policy.enabled()
                    ? Caches.inMemory(policy.maxSize(), clock)
                    : Caches.noop();
            System.out.println("装配:     " + assembled + "，TTL=" + policy.ttl());

            // 7) 兜底与禁用的大声标注
            System.out.println("兜底:     " + Caches.inMemory(100));
            System.out.println("禁用:     " + Caches.noop());
        }
    }
}
