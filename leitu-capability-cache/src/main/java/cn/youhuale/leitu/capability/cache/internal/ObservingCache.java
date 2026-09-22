package cn.youhuale.leitu.capability.cache.internal;

import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** 观察装饰器（opt-in）。外部一律经 {@code Caches.observing(...)} 获取，不直接实例化。 */
public final class ObservingCache<K, V> implements Cache<K, V> {

    private final Cache<K, V> delegate;

    private final ObservationRecorder recorder;

    private final ExecutionContextReader reader;

    public ObservingCache(Cache<K, V> delegate, ObservationRecorder recorder, ExecutionContextReader reader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 必填：装饰的是哪台缓存");
        this.recorder = Objects.requireNonNull(recorder,
                "recorder 必填：不想记观测就别包这层装饰器（默认不记，opt-in）");
        this.reader = Objects.requireNonNull(reader, "reader 必填：事件锚点（谁 + traceId）从执行上下文来");
    }

    @Override
    public Optional<V> get(K key) {
        long t0 = System.nanoTime();
        Optional<V> found = delegate.get(key);
        record(found.isPresent() ? "cache.hit" : "cache.miss", key, t0);
        return found;
    }

    @Override
    public void put(K key, V value, Duration ttl) {
        long t0 = System.nanoTime();
        delegate.put(key, value, ttl);
        record("cache.put", key, t0);
    }

    @Override
    public boolean evict(K key) {
        long t0 = System.nanoTime();
        boolean hit = delegate.evict(key);
        record(hit ? "cache.evict.hit" : "cache.evict.miss", key, t0);
        return hit;
    }

    /**
     * 覆写（关键）：先经本装饰 get（发 hit/miss）→ 命中即返；未命中则 delegate.getOrLoad——
     * 装载编排留在 delegate（在途凭证），装饰不击穿「同键只装载一次」承诺；随后记 cache.put（duration≈装载耗时）。
     * 注：miss 与 put 之间的竞态窗口内，miss 事件可能对应"他线程已装载"——事件是事实近似，装载原子性不受影响。
     */
    @Override
    public V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        Optional<V> hit = get(key);
        if (hit.isPresent()) {
            return hit.get();
        }
        long t0 = System.nanoTime();
        V value = delegate.getOrLoad(key, loader, ttl);
        record("cache.put", key, t0);
        return value;
    }

    /** 纯事实事件（无 outcome）。异常路径不记——失败形态是 failure-response 的地盘。 */
    private void record(String name, K key, long t0) {
        recorder.record(new ObservationEvent(name, reader.current(), Instant.now(),
                null, Duration.ofNanos(System.nanoTime() - t0), null,
                Map.of("cache.key", String.valueOf(key))));
    }

    @Override
    public String toString() {
        return "Observing" + delegate;
    }
}
