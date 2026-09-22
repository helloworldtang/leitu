package cn.youhuale.leitu.core.context.model;

import java.util.Objects;

/**
 * 操作者——"谁在操作"的答案主体。
 *
 * @param subject 操作者唯一标识（用户 id、服务账号、agent id）
 * @param tenant  所属租户；无租户语义用 "-"
 * @param kind    操作者类型：HUMAN（人）/ SYSTEM（系统自身）/ AGENT（AI 代理）/ ANONYMOUS（匿名＝未识别的调用方）
 */
public record Operator(String subject, String tenant, Kind kind) {

    public enum Kind { HUMAN, SYSTEM, AGENT, ANONYMOUS }

    public Operator {
        Objects.requireNonNull(subject, "subject 必填：不知道是谁就无法判定与审计；匿名请用 Operator.anonymous()");
        Objects.requireNonNull(tenant, "tenant 必填：无租户语义用 \"-\"");
        Objects.requireNonNull(kind, "kind 必填：HUMAN / SYSTEM / AGENT");
    }

    /** 人。 */
    public static Operator human(String subject, String tenant) {
        return new Operator(subject, tenant, Kind.HUMAN);
    }

    /** AI 代理——Agent 发起的操作与真人同场，但类型可区分（判定与观测需要）。 */
    public static Operator agent(String subject, String tenant) {
        return new Operator(subject, tenant, Kind.AGENT);
    }

    /** 系统自身（定时任务、内部补偿等无外部发起者的操作）。 */
    public static Operator system() {
        return new Operator("system", "-", Kind.SYSTEM);
    }

    /**
     * 匿名兜底——"未识别的调用方"。
     *
     * <p>为什么匿名<b>不是</b> HUMAN：匿名是"还没做认证"这个状态，不是一种人。
     * 把它标成 HUMAN 的后果是它能堂而皇之地走进角色判定——只要有人在角色表里给
     * "anonymous" 配了角色（或按主体名匹配时恰好放行），未认证流量就拿到了人的权限。
     * 独立成 ANONYMOUS 之后，判定层可以先验地拒绝匿名（见 RoleMapGuard），
     * 而不是指望每个 Guard 各自记得检查一个字符串。
     */
    public static Operator anonymous() {
        return new Operator("anonymous", "-", Kind.ANONYMOUS);
    }

    /** 是否匿名——按类型判，不按名字猜（真有个用户叫 anonymous 也不该被当成未认证）。 */
    public boolean isAnonymous() {
        return kind == Kind.ANONYMOUS;
    }
}
