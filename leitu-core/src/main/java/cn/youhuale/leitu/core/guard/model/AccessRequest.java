package cn.youhuale.leitu.core.guard.model;

import cn.youhuale.leitu.core.context.model.Operator;

import java.util.Objects;

/**
 * 一次待判定的访问或调用。
 *
 * <p><b>为什么要带 operator</b>：只带 subject（一个字符串）时，判定层看不见调用方的类型，
 * 于是"匿名"这种状态只能靠比对字面量 "anonymous" 去猜——猜得漏一次就是未认证流量拿到人的权限。
 * 带上 {@link Operator} 之后，判定层可以先验地拒绝匿名，不用每个 Guard 各自记着这件事。
 * 既有 4 参构造（只带 subject）保留，此时 {@link #operator()} 为空——判定层按"未知类型"处理，
 * 不为它放行任何额外权限。
 *
 * @param subject   操作者标识（"谁在操作"的产出，见 catalog 条目 who-is-operating）
 * @param action    动作（如 "order:cancel-own"）
 * @param resource  资源标识（可为数据主键、下游服务名等；无资源语义时用 "-"）
 * @param direction 入站（进来的请求）或出站（调出去的请求）——两个执行点，同一协议
 * @param operator  操作者全貌（含类型与租户）；可空，仅 4 参构造时为 null
 */
public record AccessRequest(String subject, String action, String resource, Direction direction, Operator operator) {

    public enum Direction { INBOUND, OUTBOUND }

    public AccessRequest {
        Objects.requireNonNull(subject, "subject 必填：不知道谁在操作就无法判定（见 who-is-operating）");
        Objects.requireNonNull(action, "action 必填：判定必须针对具体动作");
        Objects.requireNonNull(resource, "resource 必填：无资源语义时用 \"-\"");
        Objects.requireNonNull(direction, "direction 必填：INBOUND（入口判定）或 OUTBOUND（出站判定）");
    }

    /** 只带主体标识（无操作者全貌时用；判定层看不到类型与租户）。 */
    public AccessRequest(String subject, String action, String resource, Direction direction) {
        this(subject, action, resource, direction, null);
    }

    public static AccessRequest inbound(String subject, String action, String resource) {
        return new AccessRequest(subject, action, resource, Direction.INBOUND);
    }

    public static AccessRequest outbound(String subject, String action, String resource) {
        return new AccessRequest(subject, action, resource, Direction.OUTBOUND);
    }

    /** 带操作者全貌的入站请求——推荐用法：判定层因此看得见类型（匿名/人/系统/代理）与租户。 */
    public static AccessRequest inbound(Operator operator, String action, String resource) {
        Objects.requireNonNull(operator, "operator 必填：带全貌就用 inbound(operator, ...)；只有主体标识时用 inbound(subject, ...)");
        return new AccessRequest(operator.subject(), action, resource, Direction.INBOUND, operator);
    }

    /** 带操作者全貌的出站请求。 */
    public static AccessRequest outbound(Operator operator, String action, String resource) {
        Objects.requireNonNull(operator, "operator 必填：带全貌就用 outbound(operator, ...)；只有主体标识时用 outbound(subject, ...)");
        return new AccessRequest(operator.subject(), action, resource, Direction.OUTBOUND, operator);
    }

    /**
     * 本次调用的发起方是不是匿名（未识别的调用方）。
     *
     * <p>判定层用这个方法做先验拒绝，而不是各自去比对 "anonymous" 字面量。
     * 未携带 operator 时返回 false——没有信息就是没有信息，不替判定层猜，
     * 也不因此放行任何东西（后续的角色判定该拒绝的还是会拒绝）。
     */
    public boolean anonymous() {
        return operator != null && operator.isAnonymous();
    }
}
