package cn.youhuale.leitu.core.observe.internal;

import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;

import java.util.List;
import java.util.Objects;

/** 默认记录器实现。外部一律经 {@code ObservationRecorder.of(...)} 获取，不直接实例化本类。 */
public final class DefaultObservationRecorder implements ObservationRecorder {

    private static final LogSink LOG_DEFAULT = new LogSink();

    private static final System.Logger FAILURE_LOG = System.getLogger(LogSink.LOGGER_NAME);

    private final List<ObservationSink> sinks;

    public DefaultObservationRecorder(List<ObservationSink> sinks) {
        for (ObservationSink sink : sinks) {
            Objects.requireNonNull(sink, "sink 必填：空装配请直接 ObservationRecorder.of()——落到日志级默认");
        }
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public void record(ObservationEvent event) {
        Objects.requireNonNull(event, "event 必填：没事可记就不调用 record，而不是传 null");
        List<ObservationSink> targets = sinks.isEmpty() ? List.of(LOG_DEFAULT) : sinks;
        for (ObservationSink sink : targets) {
            try {
                sink.accept(event);
            } catch (Throwable t) {
                FAILURE_LOG.log(System.Logger.Level.WARNING,
                        "观测 sink 抛出异常，已吞下（观测不打断主流程）：sink=" + sink
                                + "，事件=" + event.name(), t);
            }
        }
    }

    @Override
    public String toString() {
        return sinks.isEmpty()
                ? "ObservationRecorder{日志级默认}"
                : "ObservationRecorder{" + sinks.size() + " sinks}";
    }
}
