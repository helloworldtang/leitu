package cn.youhuale.leitu.examples.observe;

import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.AiUsage;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定观测的标准用法语义。core 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    private final ExecutionContextReader who = ExecutionContextReader.threadLocal();

    @Test
    void 业务事件携带谁与traceId_锚点不缺席() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(events::add);
        try (var scope = ExecutionContextBinders.threadLocal()
                .bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-100"))) {
            recorder.record(ObservationEvent.of("order.cancelled", who.current(),
                    Outcome.success(), Duration.ofMillis(120)));
        }
        ObservationEvent event = events.get(0);
        assertEquals("alice", event.context().operator().subject());
        assertEquals("trace-100", event.context().traceId());
        assertTrue(event.toString().contains("trace-100"), "单行事件可 grep 到 traceId");
    }

    @Test
    void 匿名兜底同样可记_traceId自动生成() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(events::add);
        recorder.record(ObservationEvent.of("health.probed", who.current()));
        ObservationEvent event = events.get(0);
        assertTrue(event.context().operator().isAnonymous());
        assertFalse(event.context().traceId().isBlank(), "匿名兜底也要有 traceId（观测锚点不缺席）");
    }

    @Test
    void AI调用记token用量_费用走attributes() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(events::add);
        try (var scope = ExecutionContextBinders.threadLocal()
                .bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "trace-101"))) {
            recorder.record(ObservationEvent.aiCall(who.current(),
                    AiUsage.of("glm-4.7", 1248, 356), Duration.ofMillis(900),
                    Map.of("ai.cost", "0.012 CNY")));
        }
        ObservationEvent event = events.get(0);
        assertEquals(Operator.Kind.AGENT, event.context().operator().kind());
        assertEquals(1604, event.aiUsage().totalTokens());
        assertEquals("0.012 CNY", event.attributes().get("ai.cost"));
    }

    @Test
    void 判定否决也是发生了什么_失败必须带原因() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(events::add);
        GuardChain chain = GuardChain.of(req -> Decision.deny("预算耗尽，稍后重试"));
        Decision d = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "ai:invoke", "-"));
        recorder.record(ObservationEvent.of("guard.denied", who.current(),
                d.isDeny() ? Outcome.failure(d.reason()) : Outcome.success()));
        assertInstanceOf(Outcome.Failure.class, events.get(0).outcome());
        assertTrue(((Outcome.Failure) events.get(0).outcome()).reason().contains("预算耗尽"),
                "否决理由必须进事件（错误即教程的观测投影）");
    }

    @Test
    void 扇出到多落点_一个落点失败不影响其余与主流程() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(
                event -> {
                    throw new IllegalStateException("落点挂了");
                },
                events::add);
        assertDoesNotThrow(() -> recorder.record(ObservationEvent.of("app.started", who.current())));
        assertEquals(1, events.size());
    }
}
