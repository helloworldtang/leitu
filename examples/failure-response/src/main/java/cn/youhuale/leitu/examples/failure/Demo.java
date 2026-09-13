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

import java.time.Duration;

/**
 * 金样本：失败交代的最小可运行示范。
 *
 * <p>照着这个样子向调用方交代失败——这是"操作失败了怎么交代"的标准答案用法
 * （docs/problems/failure-response.md；消费 who-is-operating / allow-or-not / what-happened）。
 */
public final class Demo {

    public static void main(String[] args) {
        var binder = ExecutionContextBinders.threadLocal();
        ExecutionContextReader who = ExecutionContextReader.threadLocal();
        ObservationRecorder recorder = ObservationRecorder.of(event -> System.out.println("观测:     " + event));

        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-100"))) {

            // 1) 业务错全量交代：调用方的错，说出来无损失（默认不重试——改行为）
            FailureNotice notFound = FailureNotice.business("order.not-found", "订单不存在",
                    "订单 ord-9527 不存在或已删除；请确认订单号后重试", who.current());
            System.out.println("业务错:   " + notFound);

            // 2) 判定链否决转交代：理由与重试语义随行（Decision 对齐）
            GuardChain guard = GuardChain.of(req ->
                    Decision.deny("预算耗尽，稍后重试", Retry.later(Duration.ofSeconds(30))));
            Decision d = guard.check(AccessRequest.inbound(
                    who.current().operator().subject(), "ai:invoke", "-"));
            FailureNotice denied = FailureNotice.fromDecision(d, who.current());
            System.out.println("否决交代: " + denied);

            // 3) 系统错脱敏默认：异常消息与类名不出端——全量细节在日志与观测，调用方凭 traceId 查证
            try {
                throw new IllegalStateException(
                        "jdbc:mysql://prod-db:3306/order?user=root&password=s3cret 查询超时");
            } catch (RuntimeException e) {
                FailureNotice systemFailure = FailureNotice.fromThrowable(e, who.current());
                System.out.println("系统错:   " + systemFailure);
            }

            // 4) 管道一条龙：判定 → 记录（对内全量）→ 交代（对外脱敏/全量按 kind）
            recorder.record(ObservationEvent.of("guard.denied", who.current(),
                    Outcome.failure(d.reason())));
            System.out.println("对外交代: " + FailureNotice.fromDecision(d, who.current()));
        }
    }
}
