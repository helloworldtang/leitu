package cn.youhuale.leitu.core.guard.api;

import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.guard.model.Decision;

import java.util.Objects;

/**
 * 判定链否决的载体异常（传输无关）——"把否决说成失败"的标准姿势。
 *
 * <p>判定链本身只回答"允许吗"（{@link GuardChain#check}），不替调用方决定"否决了怎么办"：
 * 入口可投影成 HTTP 403（见 adapter-web 的失败投影）、消费者可跳过这条消息、CLI 可打印后退出。
 * 本类让"否决"能跨越这些边界传播，且一路带着 {@link Decision} 的 reason（为什么）与 retry（怎么办）。
 *
 * <p>用法：
 * <pre>{@code
 * Decision d = chain.check(AccessRequest.inbound(who, "order:cancel", "order-42"));
 * if (d.isDeny()) {
 *     throw AccessDeniedException.from(d, reader);
 * }
 * }</pre>
 *
 * <p>装配错误大声失败：把"允许"的判定包进来即抛 {@link IllegalArgumentException}——
 * 放行不是失败，别用它（与 {@code FailureNotice.fromDecision} 同一纪律）。
 */
public class AccessDeniedException extends RuntimeException {

    private final Decision decision;

    private final ExecutionContext context;

    public AccessDeniedException(Decision decision, ExecutionContext context) {
        super(Objects.requireNonNull(decision, "decision 必填：要传播的是哪次判定").reason());
        if (decision.isAllow()) {
            throw new IllegalArgumentException(
                    "允许的 Decision 不是失败：放行无需抛异常（装配错误大声失败）——先判 isDeny() 再抛");
        }
        this.decision = decision;
        this.context = Objects.requireNonNull(context,
                "context 必填：否决要带 traceId 才查得证——传 ExecutionContextReader.current()");
    }

    /** 本次否决的判定（含 reason 与 retry）。 */
    public Decision decision() {
        return decision;
    }

    /** 判定发生时的执行上下文（traceId 从这来）。 */
    public ExecutionContext context() {
        return context;
    }

    /** 从读取器取上下文的便捷构造（与 {@code FailureNoticeException.from} 同款）。 */
    public static AccessDeniedException from(Decision decision, ExecutionContextReader who) {
        Objects.requireNonNull(who, "who 必填：上下文从执行上下文来——传注入的 ExecutionContextReader");
        return new AccessDeniedException(decision, who.current());
    }

    @Override
    public String toString() {
        return "AccessDeniedException{" + decision + "} trace=" + context.traceId();
    }
}
