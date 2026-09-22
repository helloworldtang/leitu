package cn.youhuale.leitu.capability.data.internal;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.model.PageRequest;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** 观察装饰器（opt-in）。外部一律经 {@code DataStores.observing(...)} 获取，不直接实例化。 */
public final class ObservingDataStore<T extends Auditable, ID> implements DataStore<T, ID> {

    private final DataStore<T, ID> delegate;

    private final Function<T, ID> idOf;

    private final ObservationRecorder recorder;

    private final ExecutionContextReader reader;

    public ObservingDataStore(DataStore<T, ID> delegate, Function<T, ID> idOf,
                              ObservationRecorder recorder, ExecutionContextReader reader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 必填：装饰的是哪个存取器");
        this.idOf = Objects.requireNonNull(idOf, "idOf 必填：事件 attributes 要带 data.id——传 Order::id");
        this.recorder = Objects.requireNonNull(recorder,
                "recorder 必填：不想记观测就别包这层装饰器（默认不记，opt-in）");
        this.reader = Objects.requireNonNull(reader, "reader 必填：事件锚点（谁 + traceId）从执行上下文来");
    }

    @Override
    public T save(T entity) {
        long t0 = System.nanoTime();
        T saved = delegate.save(entity);
        record("data.saved", Map.of("data.id", String.valueOf(idOf.apply(saved))), t0);
        return saved;
    }

    @Override
    public Optional<T> findById(ID id) {
        long t0 = System.nanoTime();
        Optional<T> found = delegate.findById(id);
        record(found.isPresent() ? "data.read.hit" : "data.read.miss",
                Map.of("data.id", String.valueOf(id)), t0);
        return found;
    }

    @Override
    public boolean deleteById(ID id) {
        long t0 = System.nanoTime();
        boolean hit = delegate.deleteById(id);
        record(hit ? "data.deleted.hit" : "data.deleted.miss",
                Map.of("data.id", String.valueOf(id)), t0);
        return hit;
    }

    @Override
    public List<T> findAll() {
        long t0 = System.nanoTime();
        List<T> all = delegate.findAll();
        record("data.listed", listed(all.size(), null), t0);
        return all;
    }

    /** 翻页同样记 {@code data.listed}，attributes 带上 offset/limit——事后回溯"当时读的是哪一页"。 */
    @Override
    public List<T> findAll(PageRequest page) {
        Objects.requireNonNull(page, "page 必填：要哪一页说清楚——传 PageRequest.first(50)");
        long t0 = System.nanoTime();
        List<T> all = delegate.findAll(page);
        record("data.listed", listed(all.size(), page), t0);
        return all;
    }

    private static Map<String, String> listed(int size, PageRequest page) {
        if (page == null) {
            return Map.of("data.count", String.valueOf(size));
        }
        return Map.of("data.count", String.valueOf(size),
                "data.offset", String.valueOf(page.offset()),
                "data.limit", String.valueOf(page.limit()));
    }

    /** 纯事实事件（无 outcome）。异常路径不记——失败形态是 failure-response 的地盘（见问题页边界）。 */
    private void record(String name, Map<String, String> attributes, long t0) {
        recorder.record(new ObservationEvent(name, reader.current(), Instant.now(),
                null, Duration.ofNanos(System.nanoTime() - t0), null, attributes));
    }

    @Override
    public String toString() {
        return "Observing" + delegate;
    }
}
