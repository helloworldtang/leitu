package cn.youhuale.leitu.core.observe;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.internal.LogSink;
import cn.youhuale.leitu.core.observe.model.AiUsage;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 观测答案的单测：锁事件协议的构造纪律与记录器的兜底语义。 */
class ObservationRecorderTest {

    private final ExecutionContext ctx = ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1");

    @Test
    void 事件名必须点分命名_构造即校验并教修复方式() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ObservationEvent.of("startup", ctx));
        assertTrue(e.getMessage().contains("点分"), "异常必须即教程：" + e.getMessage());
        assertTrue(e.getMessage().contains("当前：startup"), "必须报出当前值：" + e.getMessage());
    }

    @Test
    void context锚点必填_null即教程异常() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> ObservationEvent.of("app.started", null));
        assertTrue(e.getMessage().contains("必填"), "异常必须即教程：" + e.getMessage());
        assertTrue(e.getMessage().contains("匿名兜底"), "必须给出出路：" + e.getMessage());
    }

    @Test
    void attributes防御拷贝_null转空Map() {
        Map<String, String> src = new HashMap<>();
        src.put("k", "v");
        ObservationEvent event = new ObservationEvent("app.started", ctx, Instant.now(), null, null, null, src);
        src.put("k2", "漂移");
        assertEquals(Map.of("k", "v"), event.attributes(), "构造后改源 map 不得影响事件");

        ObservationEvent nullAttrs = new ObservationEvent("app.started", ctx, Instant.now(), null, null, null, null);
        assertTrue(nullAttrs.attributes().isEmpty(), "null attributes 转空 Map");
    }

    @Test
    void duration不能为负() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ObservationEvent.of("job.finished", ctx, Outcome.success(), Duration.ofMillis(-1)));
        assertTrue(e.getMessage().contains("不能为负"), "异常必须即教程：" + e.getMessage());
    }

    @Test
    void Failure必须带原因_空白也不行() {
        NullPointerException npe = assertThrows(NullPointerException.class, () -> Outcome.failure(null));
        assertTrue(npe.getMessage().contains("错误即教程"));
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
                () -> Outcome.failure("  "));
        assertTrue(iae.getMessage().contains("错误即教程"));
        assertEquals("成功", Outcome.success().toString());
    }

    @Test
    void token数不能为负_未知记零() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AiUsage.of("glm-4.7", -1, 0));
        assertTrue(e.getMessage().contains("未知记 0"), "异常必须即教程：" + e.getMessage());
        assertEquals(1604, AiUsage.of("glm-4.7", 1248, 356).totalTokens());
        assertEquals(0, AiUsage.of("m", 0, 0).totalTokens());
    }

    @Test
    void aiCall工厂生成标准名与成功结果() {
        ObservationEvent event = ObservationEvent.aiCall(ctx,
                AiUsage.of("glm-4.7", 100, 50), Duration.ofMillis(900), null);
        assertEquals("ai.invocation", event.name());
        assertInstanceOf(Outcome.Success.class, event.outcome());
        assertNotNull(event.occurredAt());
        assertNotNull(event.duration());
    }

    @Test
    void 日志级默认_单行事件进JDK日志_失败升WARNING() {
        List<String> lines = new ArrayList<>();
        List<System.Logger.Level> levels = new ArrayList<>();
        System.Logger fake = new System.Logger() {
            @Override
            public String getName() {
                return "fake";
            }

            @Override
            public boolean isLoggable(System.Logger.Level level) {
                return true;
            }

            @Override
            public void log(System.Logger.Level level, java.util.ResourceBundle bundle,
                            String format, Object... params) {
                levels.add(level);
                lines.add(format);
            }

            @Override
            public void log(System.Logger.Level level, java.util.ResourceBundle bundle,
                            String msg, Throwable thrown) {
                levels.add(level);
                lines.add(msg);
            }
        };
        LogSink sink = new LogSink(fake);
        sink.accept(ObservationEvent.of("order.cancelled", ctx, Outcome.success(), Duration.ofMillis(120)));
        sink.accept(ObservationEvent.of("job.failed", ctx, Outcome.failure("下游超时")));
        assertEquals(System.Logger.Level.INFO, levels.get(0));
        assertEquals(System.Logger.Level.WARNING, levels.get(1));
        assertTrue(lines.get(0).contains("order.cancelled"), "单行事件含事件名");
        assertTrue(lines.get(0).contains("t-1"), "单行事件含 traceId（锚点可 grep）");
        assertTrue(lines.get(0).contains("alice"), "单行事件含操作者");
    }

    @Test
    void 空装配落到日志级默认_记录不抛异常() {
        ObservationRecorder recorder = ObservationRecorder.of();
        assertDoesNotThrow(() -> recorder.record(ObservationEvent.of("app.started", ctx)));
    }

    @Test
    void sink抛异常被吞_不打断主流程_扇出继续() {
        List<ObservationEvent> received = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(
                event -> {
                    throw new IllegalStateException("落点挂了");
                },
                received::add);
        assertDoesNotThrow(() -> recorder.record(ObservationEvent.of("order.cancelled", ctx)));
        assertEquals(1, received.size(), "一个落点失败不影响其余落点收到事件");
    }

    @Test
    void sink为null视为装配错误_消息给出日志级出路() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> ObservationRecorder.of((ObservationSink) null));
        assertTrue(e.getMessage().contains("ObservationRecorder.of()"), "必须给出修复方式：" + e.getMessage());
    }

    @Test
    void record传null事件大声失败() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> ObservationRecorder.of().record(null));
        assertTrue(e.getMessage().contains("必填"), "异常必须即教程：" + e.getMessage());
    }
}
