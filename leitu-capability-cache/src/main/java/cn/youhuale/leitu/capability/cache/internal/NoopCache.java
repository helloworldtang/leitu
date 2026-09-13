package cn.youhuale.leitu.capability.cache.internal;

import cn.youhuale.leitu.capability.cache.spi.Cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Noop 实现——零开销禁用（无害维度可 Noop，GLOSSARY 分维度默认值的兑现）。外部经 {@code Caches.noop()} 获取，不直接实例化。 */
public final class NoopCache<K, V> implements Cache<K, V> {

    @Override
    public Optional<V> get(K key) {
        Objects.requireNonNull(key, "key 必填：缺失语义是 Optional.empty()，不是 null 入参");
        return Optional.empty();
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        // 参数合同不因 Noop 松动：配错在禁用态也要 fail-fast（换实现不换错误）
        Objects.requireNonNull(key, "key 必填：无键无法定位缓存条目");
        Objects.requireNonNull(value, "value 必填：缓存不存 null 值");
        Cache.requirePositiveTtl(ttl);
    }

    @Override
    public boolean evict(K key) {
        Objects.requireNonNull(key, "key 必填：未见语义是 false，不是 null 入参");
        return false;
    }

    /** 每次装载，绝不缓存。 */
    @Override
    public V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        Objects.requireNonNull(key, "key 必填：缺失语义是 Optional.empty()，不是 null 入参");
        Objects.requireNonNull(loader, "loader 必填：只读就改用 get(...)");
        Cache.requirePositiveTtl(ttl);
        return Objects.requireNonNull(loader.apply(key),
                "装载器不能返回 null：缓存不存 null 值——\"无值\"语义请不缓存（每次装载）");
    }

    @Override
    public String toString() {
        return "Cache{Noop——零开销禁用（无害维度默认值，见 GLOSSARY）}";
    }
}
