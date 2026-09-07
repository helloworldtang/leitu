package cn.youhuale.leitu.capability.access.internal;

import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.capability.access.spi.PermissionPolicy;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;

import java.util.Objects;

/** 声明式 RBAC-lite 的判定实现。外部经 AccessGuards.roleMap(RoleMap) 获取。 */
public final class RoleMapGuard implements PermissionPolicy {

    private final RoleMap map;

    public RoleMapGuard(RoleMap map) {
        this.map = Objects.requireNonNull(map, "map 必填：用 RoleMap.create()...build() 构造");
    }

    @Override
    public Decision check(AccessRequest request) {
        var required = map.rolesRequiredBy(request.action());
        if (required.isEmpty()) {
            return Decision.deny("动作 " + request.action() + " 未配置任何放行规则——按安全默认拒绝。"
                    + "修复：RoleMap.create().action(\"" + request.action() + "\").allowsRoles(...)");
        }
        if (map.permits(request.action(), request.subject())) {
            return Decision.allow();
        }
        return Decision.deny("主体 " + request.subject() + "（角色:" + map.rolesOfSubject(request.subject())
                + "）不满足 " + request.action() + " 所需角色（" + required + "）——按安全默认拒绝");
    }

    @Override
    public String toString() {
        return "RoleMapGuard";
    }
}
