package cn.youhuale.leitu.core.context.model;

import java.util.Objects;

/**
 * 操作者——"谁在操作"的答案主体。
 *
 * @param subject 操作者唯一标识（用户 id、服务账号、agent id）
 * @param tenant  所属租户；无租户语义用 "-"
 * @param kind    操作者类型：HUMAN（人）/ SYSTEM（系统自身）/ AGENT（AI 代理）
 */
public record Operator(String subject, String tenant, Kind kind) {

    public enum Kind { HUMAN, SYSTEM, AGENT }

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

    /** 匿名兜底。 */
    public static Operator anonymous() {
        return new Operator("anonymous", "-", Kind.HUMAN);
    }

    public boolean isAnonymous() {
        return "anonymous".equals(subject);
    }
}
