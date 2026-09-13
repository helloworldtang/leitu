package cn.youhuale.leitu.core.observe.api;

import cn.youhuale.leitu.core.observe.internal.DefaultObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;

import java.util.Arrays;

/**
 * 端口：记录"发生了什么"——业务代码认识观测的全部接口。
 *
 * <p>一次 record 扇出到全部落点；落点异常被吞并大声记错（观测不打断主流程）。
 *
 * <p>用法：
 * <pre> {@code
 * ObservationRecorder recorder;  // 注入
 * recorder.record(ObservationEvent.of("order.cancelled", who.current(),
 *         Outcome.success(), Duration.ofMillis(120)));
 * }</pre>
 */
public interface ObservationRecorder {

    /**
     * 记录一个事件。event 为 null 视为编程错误，立即失败并指明修复方式；
     * 落点侧异常不会传播到调用方。
     */
    void record(ObservationEvent event);

    /**
     * 装配记录器。空数组同样得到一台可用的记录器——事件落到 JDK 日志
     * （日志级默认：观测维度=最小可用实现，非 Noop，见 GLOSSARY 分维度默认值）。
     */
    static ObservationRecorder of(ObservationSink... sinks) {
        return new DefaultObservationRecorder(Arrays.asList(sinks));
    }
}
