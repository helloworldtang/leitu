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
import java.util.function.Function;

/** 进程内默认实现：TTL 惰性过期 + LRU 上限 + 租户作用域键 + 同键并发只装载一次。外部经 {@code Caches.inMemory(...)} 获取，不直接实例化。 */
public final class InMemoryCache<K, V> implements Cache<K, V> {

    private record ScopedKey(String tenant, Object key) {
    }

    private record CachedEntry<V>(V value, Instant expiresAt) {
    }

    private final long maxSize;

    private final ExecutionContextReader reader;

    private final Clock clock;

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
     * 装载三段式的原子版：与 get/put 同一把锁，同键并发只装载一次（防击穿）。
     * 粗粒度互斥——装载期间阻塞本缓存一切操作，正确性优先（见问题页边界；高并发装载出路是 adapter 或 lock 能力）。
     * 装载失败不缓存不吞——异常原样上抛。
     */
    @Override
    public V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        Objects.requireNonNull(key, "key 必填：缺失语义是 Optional.empty()，不是 null 入参");
        Objects.requireNonNull(loader, "loader 必填：只读不装就改用 get(...)");
        Cache.requirePositiveTtl(ttl);
        synchronized (map) {
            ScopedKey scoped = scoped(key);
            CachedEntry<V> entry = map.get(scoped);
            if (entry != null && !expired(entry)) {
                return entry.value();
            }
            if (entry != null) {
                map.remove(scoped);         // 过期条目惰性清除
            }
            V loaded = Objects.requireNonNull(loader.apply(key),
                    "装载器不能返回 null：缓存不存 null 值——\"无值\"语义请不缓存（每次装载）");
            map.put(scoped, new CachedEntry(loaded, clock.instant().plus(ttl)));
            return loaded;
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
