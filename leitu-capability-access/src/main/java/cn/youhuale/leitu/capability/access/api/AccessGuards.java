package cn.youhuale.leitu.capability.access.api;

import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.capability.access.spi.PermissionPolicy;
import cn.youhuale.leitu.core.guard.spi.Guard;

/**
 * access 能力的工厂——从 api 取 Guard，不触碰 internal。
 *
 * <p>用法：
 * <pre>{@code
 * GuardChain chain = GuardChain.of(
 *         AccessGuards.roleMap(roleMap),
 *         AccessGuards.budget("ai-calls", BudgetSpec.of(100, Duration.ofMinutes(1))));
 * }</pre>
 */
public final class AccessGuards {

    private AccessGuards() {
    }

    /** 由声明式映射构造权限 Guard（RBAC-lite 默认实现；未匹配=默认拒绝）。 */
    public static PermissionPolicy roleMap(RoleMap map) {
        return new cn.youhuale.leitu.capability.access.internal.RoleMapGuard(map);
    }

    /** 预算 Guard：窗口限次，耗尽后否决并携带重试语义。name 用于否决理由的可读性。 */
    public static Guard budget(String name, BudgetSpec spec) {
        return new cn.youhuale.leitu.capability.access.internal.BudgetGuard(name, spec);
    }
}
