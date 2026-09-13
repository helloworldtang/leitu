package cn.youhuale.leitu.capability.cache.model;

import cn.youhuale.leitu.core.config.api.ConfigReader;

import java.time.Duration;

/**
 * 缓存策略——缓存参数从配置来的标准读法（兑现 catalog 条目 cache 对 config-source 的依赖）。
 *
 * <p>配置键（点分命名，随 {@code datasource.pool} 同款惯例）：
 * {@code cache.max-size}（缺省 1000）/ {@code cache.ttl}（缺省 PT30M，ISO-8601）/
 * {@code cache.enabled}（缺省 true）。
 *
 * <p>用法（装配处显式分支，决策可见）：
 * <pre>{@code
 * CachePolicy policy = CachePolicy.fromConfig(config);
 * Cache<String, User> users = policy.enabled()
 *         ? Caches.inMemory(policy.maxSize())
 *         : Caches.noop();
 * users.getOrLoad("u-1", this::loadUser, policy.ttl());
 * }</pre>
 */
public record CachePolicy(long maxSize, Duration ttl, boolean enabled) {

    public CachePolicy {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize 必须为正（如 1000）：无上限缓存是内存 footgun。当前：" + maxSize);
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl 必须为正（ISO-8601 如 PT30M）。当前：" + ttl);
        }
    }

    /** 从配置读策略：键缺失走缺省（1000 / PT30M / true）；有值但解析失败=错误即教程（ConfigReader 教学）。 */
    public static CachePolicy fromConfig(ConfigReader config) {
        return new CachePolicy(
                config.getInt("cache.max-size", 1000),
                config.getDuration("cache.ttl", Duration.ofMinutes(30)),
                config.getBoolean("cache.enabled", true));
    }
}
