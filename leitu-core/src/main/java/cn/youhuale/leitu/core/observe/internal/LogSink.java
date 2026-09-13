package cn.youhuale.leitu.core.observe.internal;

import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;

import java.util.Objects;

/** 日志级默认落点：System.Logger 单行事件（失败事件升 WARNING）。外部经 {@code ObservationRecorder.of()} 空装配间接触达，不直接实例化。 */
public final class LogSink implements ObservationSink {

    static final String LOGGER_NAME = "cn.youhuale.leitu.observe";

    private final System.Logger logger;

    public LogSink() {
        this(System.getLogger(LOGGER_NAME));
    }

    /** 指定 logger（测试可注入 fake）。 */
    public LogSink(System.Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger 必填");
    }

    @Override
    public void accept(ObservationEvent event) {
        logger.log(levelOf(event), event.toString());
    }

    private static System.Logger.Level levelOf(ObservationEvent event) {
        return event.outcome() instanceof Outcome.Failure
                ? System.Logger.Level.WARNING
                : System.Logger.Level.INFO;
    }
}
