package cn.youhuale.leitu.core.context.model;

import java.util.Objects;
import java.util.UUID;

/**
 * 执行上下文——一次操作的"谁在操作 + 链路标识"束。
 *
 * <p>身份、租户在 {@link Operator} 里；traceId 是本次调用链的锚点（观测三支柱的关联键）。
 * 用 {@link #of(Operator)} 在入口处创建，traceId 缺省自动生成。
 */
public record ExecutionContext(Operator operator, String traceId) {

    public ExecutionContext {
        Objects.requireNonNull(operator, "operator 必填：匿名请用 ExecutionContext.anonymous()");
        Objects.requireNonNull(traceId, "traceId 必填：入口处不知来源时用 of(operator) 自动生成");
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空白");
        }
    }

    /** 显式 traceId。 */
    public static ExecutionContext of(Operator operator, String traceId) {
        return new ExecutionContext(operator, traceId);
    }

    /** 自动生成 traceId（入口处用）。 */
    public static ExecutionContext of(Operator operator) {
        return new ExecutionContext(operator, UUID.randomUUID().toString());
    }

    /** 匿名兜底：未绑定任何上下文时的答案。 */
    public static ExecutionContext anonymous() {
        return of(Operator.anonymous());
    }
}
