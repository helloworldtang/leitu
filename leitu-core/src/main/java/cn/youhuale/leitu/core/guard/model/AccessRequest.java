package cn.youhuale.leitu.core.guard.model;

import java.util.Objects;

/**
 * 一次待判定的访问或调用。
 *
 * @param subject   操作者标识（"谁在操作"的产出，见 catalog 条目 who-is-operating）
 * @param action    动作（如 "order:cancel-own"）
 * @param resource  资源标识（可为数据主键、下游服务名等；无资源语义时用 "-"）
 * @param direction 入站（进来的请求）或出站（调出去的请求）——两个执行点，同一协议
 */
public record AccessRequest(String subject, String action, String resource, Direction direction) {

    public enum Direction { INBOUND, OUTBOUND }

    public AccessRequest {
        Objects.requireNonNull(subject, "subject 必填：不知道谁在操作就无法判定（见 who-is-operating）");
        Objects.requireNonNull(action, "action 必填：判定必须针对具体动作");
        Objects.requireNonNull(resource, "resource 必填：无资源语义时用 \"-\"");
        Objects.requireNonNull(direction, "direction 必填：INBOUND（入口判定）或 OUTBOUND（出站判定）");
    }

    public static AccessRequest inbound(String subject, String action, String resource) {
        return new AccessRequest(subject, action, resource, Direction.INBOUND);
    }

    public static AccessRequest outbound(String subject, String action, String resource) {
        return new AccessRequest(subject, action, resource, Direction.OUTBOUND);
    }
}
