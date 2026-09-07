package cn.youhuale.leitu.examples.access;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;

import java.time.Duration;

/**
 * 金样本：access 能力的最小可运行示范——"谁能做什么"一张表 + 预算限次，
 * 两个 Guard 组成判定链（权限先拦，fail-fast 保护预算配额）。
 */
public final class Demo {

    public static void main(String[] args) {
        RoleMap map = RoleMap.create()
                .subject("alice").hasRoles("admin")
                .subject("bob").hasRoles("member")
                .action("tool:web-search").allowsRoles("admin", "member")
                .build();

        GuardChain chain = GuardChain.of(
                AccessGuards.roleMap(map),
                AccessGuards.budget("ai-calls", BudgetSpec.of(3, Duration.ofMinutes(1))));

        // 1) 角色命中 + 预算内 → 允许
        System.out.println(chain.check(AccessRequest.inbound("bob", "tool:web-search", "-")));
        // 2) 未配置动作 → 默认拒绝（理由带配置修复路径）
        System.out.println(chain.check(AccessRequest.inbound("alice", "tool:rm-rf", "-")));
        // 3) 角色过、预算耗尽 → 否决 + 稍后重试
        for (int i = 0; i < 3; i++) {
            chain.check(AccessRequest.inbound("bob", "tool:web-search", "-"));
        }
        System.out.println(chain.check(AccessRequest.inbound("bob", "tool:web-search", "-")));
    }
}
