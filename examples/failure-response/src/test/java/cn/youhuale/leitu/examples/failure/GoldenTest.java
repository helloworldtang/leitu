package cn.youhuale.leitu.examples.failure;

import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定失败交代的标准用法语义。core 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    private static final cn.youhuale.leitu.core.context.spi.ExecutionContextBinder BINDER =
            ExecutionContextBinders.threadLocal();
    private final ExecutionContextReader who = ExecutionContextReader.threadLocal();

    @Test
    void 业务错全量交代_默认不重试() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            FailureNotice notice = FailureNotice.business("order.not-found", "订单不存在",
                    "订单 ord-9527 不存在或已删除；请确认订单号后重试", who.current());
            assertTrue(notice.isBusiness());
            assertTrue(notice.detail().contains("ord-9527"), "业务错全量交代");
            assertInstanceOf(Retry.Never.class, notice.retry());
        }
    }

    @Test
    void 判定链否决转交代_理由与重试语义随行() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            GuardChain guard = GuardChain.of(req ->
                    Decision.deny("预算耗尽，稍后重试", Retry.later(Duration.ofSeconds(30))));
            Decision d = guard.check(AccessRequest.inbound(
                    who.current().operator().subject(), "ai:invoke", "-"));
            FailureNotice notice = FailureNotice.fromDecision(d, who.current());
            assertEquals("guard.denied", notice.type());
            assertEquals(d.reason(), notice.detail(), "否决理由原样进交代");
            Retry.Later later = assertInstanceOf(Retry.Later.class, notice.retry());
            assertEquals(Duration.ofSeconds(30), later.after());
        }
    }

    @Test
    void 系统错脱敏默认_异常细节不出端() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            Throwable boom = new IllegalStateException(
                    "jdbc:mysql://prod-db:3306/order?user=root&password=s3cret 查询超时");
            FailureNotice notice = FailureNotice.fromThrowable(boom, who.current());
            assertTrue(notice.isSystem());
            assertEquals("system.unexpected", notice.type());
            assertFalse(notice.toString().contains("jdbc"), "异常消息不出端");
            assertFalse(notice.toString().contains("s3cret"), "凭据不出端");
            assertFalse(notice.toString().contains("IllegalState"), "类名不出端");
        }
    }

    @Test
    void 允许的Decision不是失败_装配错误大声失败() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> FailureNotice.fromDecision(Decision.allow(), who.current()));
            assertTrue(e.getMessage().contains("不是失败"));
        }
    }

    @Test
    void 判定到交代到记录一条龙_对内全量对外交代() {
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = events::add;
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-9"))) {
            Decision d = GuardChain.of(req -> Decision.deny("预算耗尽", Retry.later(Duration.ofSeconds(30))))
                    .check(AccessRequest.inbound(who.current().operator().subject(), "ai:invoke", "-"));
            // 对内记录：全量（异常消息也可入观测 attributes——观测是内部通道）
            recorder.record(ObservationEvent.of("guard.denied", who.current(), Outcome.failure(d.reason())));
            Throwable boom = new IllegalStateException("jdbc:mysql://prod?password=s3cret 超时");
            recorder.record(new ObservationEvent("system.failure", who.current(), java.time.Instant.now(),
                    Outcome.failure(boom.getMessage()), null, null, Map.of()));
            // 对外交代：脱敏
            FailureNotice notice = FailureNotice.fromThrowable(boom, who.current());

            assertEquals(2, events.size());
            assertEquals("alice", events.get(0).context().operator().subject(), "对内全量：锚点齐备");
            assertTrue(((Outcome.Failure) events.get(1).outcome()).reason().contains("s3cret"),
                    "对内全量：异常消息进观测（观测是内部通道）");
            assertEquals("trace-9", notice.traceId(), "对外只带 traceId——调用方凭它查证");
            assertFalse(notice.toString().contains("s3cret"), "对外脱敏——同一事实，两个通道两种尺度");
        }
    }
}
