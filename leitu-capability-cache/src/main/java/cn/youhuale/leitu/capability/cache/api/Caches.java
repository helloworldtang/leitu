package cn.youhuale.leitu.capability.cache.api;

import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;

import java.time.Clock;

/**
 * 缓存的工厂——业务/宿主从 api 取实现，不触碰 internal。
 *
 * <p>用法（装配处显式分支，决策可见）：
 * <pre>{@code
 * CachePolicy policy = CachePolicy.fromConfig(config);
 * Cache<String, User> users = policy.enabled()
 *         ? Caches.inMemory(policy.maxSize())
 *         : Caches.noop();
 * users.getOrLoad("u-1", this::loadUser, policy.ttl());
 * }</pre>
 *
 * <p>分布式缓存（Redis）由 adapter 实现 {@link Cache}（见 docs/problems/cache.md）。
 */
public final class Caches {

    private Caches() {
    }

    /** 进程内默认实现（TTL 惰性过期 + LRU 上限 + 同键只装载一次）；时钟取系统 UTC，读取器回落全局线程绑定。 */
    public static <K, V> Cache<K, V> inMemory(long maxSize) {
        return inMemory(maxSize, Clock.systemUTC());
    }

    /** 进程内默认实现，可注入时钟（测试断言 TTL 过期用）。 */
    public static <K, V> Cache<K, V> inMemory(long maxSize, Clock clock) {
        return inMemory(maxSize, ExecutionContextReader.threadLocal(), clock);
    }

    /**
     * 进程内默认实现，读取器与时钟全部由装配方给出——<b>非 Spring 场景或自定义绑定机制走这个重载</b>。
     *
     * <p>读取器决定租户作用域键：缓存键是 (租户, key)，读错了人就是跨租户串味。
     * 两参/单参重载回落到全局线程绑定，只在默认线程级 binder 下成立。
     */
    public static <K, V> Cache<K, V> inMemory(long maxSize, ExecutionContextReader reader, Clock clock) {
        return new cn.youhuale.leitu.capability.cache.internal.InMemoryCache<>(maxSize, reader, clock);
    }

    /** 观察装饰（opt-in，默认不记）：cache.hit / cache.miss / cache.put / cache.evict.hit / cache.evict.miss。 */
    public static <K, V> Cache<K, V> observing(Cache<K, V> delegate, ObservationRecorder recorder) {
        return observing(delegate, recorder, ExecutionContextReader.threadLocal());
    }

    /** 观察装饰，读取器由装配方给出（与 {@link #inMemory(long, ExecutionContextReader, Clock)} 同款契约）。 */
    public static <K, V> Cache<K, V> observing(Cache<K, V> delegate, ObservationRecorder recorder,
                                               ExecutionContextReader reader) {
        return new cn.youhuale.leitu.capability.cache.internal.ObservingCache<>(delegate, recorder, reader);
    }

    /** Noop——零开销禁用（无害维度默认值；enabled=false 的装配答案）。 */
    public static <K, V> Cache<K, V> noop() {
        return new cn.youhuale.leitu.capability.cache.internal.NoopCache<>();
    }
}
