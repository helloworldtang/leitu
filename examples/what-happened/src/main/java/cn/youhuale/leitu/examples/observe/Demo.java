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

import java.time.Duration;
import java.util.Map;

/**
 * 金样本：观测的最小可运行示范。
 *
 * <p>照着这个样子记你的事件——这是"发生了什么"的标准答案用法
 * （docs/problems/what-happened.md；消费 who-is-operating 与 allow-or-not）。
 */
public final class Demo {

    public static void main(String[] args) {
        var binder = ExecutionContextBinders.threadLocal();
        ExecutionContextReader who = ExecutionContextReader.threadLocal();
        ObservationRecorder recorder = ObservationRecorder.of(event -> System.out.println("观测: " + event));

        // 1) 业务事件：绑定的上下文自动成为锚点（谁 + traceId）→ 单行可 grep
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-100"))) {
            recorder.record(ObservationEvent.of("order.cancelled", who.current(),
                    Outcome.success(), Duration.ofMillis(120)));
        }

        // 2) 入口未绑定 → 匿名兜底同样可记 → 锚点永不缺席（anonymous + 自动 traceId）
        recorder.record(ObservationEvent.of("health.probed", who.current()));

        // 3) AI 调用成本：AGENT 是一等操作者，token 是事实、费用走 attributes
        try (var scope = binder.bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "trace-101"))) {
            recorder.record(ObservationEvent.aiCall(who.current(),
                    AiUsage.of("glm-4.7", 1248, 356), Duration.ofMillis(900),
                    Map.of("ai.cost", "0.012 CNY")));
        }

        // 4) 判定也是"发生了什么"：guard 否决记为失败事件（reason 进事件）
        GuardChain chain = GuardChain.of(req -> Decision.deny("预算耗尽，稍后重试"));
        Decision d = chain.check(AccessRequest.inbound(who.current().operator().subject(), "ai:invoke", "-"));
        recorder.record(ObservationEvent.of("guard.denied", who.current(),
                d.isDeny() ? Outcome.failure(d.reason()) : Outcome.success()));

        // 5) 什么都没装配 → 日志级默认（非 Noop）→ 事件进 JDK 日志
        ObservationRecorder.of().record(ObservationEvent.of("demo.finished", who.current()));
    }
}
