package cn.youhuale.leitu.capability.access.spi;

import cn.youhuale.leitu.core.guard.spi.Guard;

/**
 * 权限判定的扩展缝（ADR-005）。
 *
 * <p>实现它即是一个可插进判定链的 Guard。默认实现是声明式 RBAC-lite
 * （{@code AccessGuards.roleMap(RoleMap)}）；换模型（ABAC、ReBAC 引擎、外部策略服务）
 * 时实现本接口，业务代码与判定链零改动。
 *
 * <p>合同同 Guard：必给判定、否决必须教人、不抛异常、线程安全。
 */
public interface PermissionPolicy extends Guard {
}
