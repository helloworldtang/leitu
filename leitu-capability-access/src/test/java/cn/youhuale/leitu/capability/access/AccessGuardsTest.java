package cn.youhuale.leitu.capability.access;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AccessGuardsTest {

    static final RoleMap MAP = RoleMap.create()
            .subject("alice").hasRoles("admin")
            .subject("bob").hasRoles("member")
            .action("order:cancel").allowsRoles("admin")
            .action("order:view").allowsRoles("admin", "member")
            .build();

    @Test
    void 角色命中放行() {
        Decision d = AccessGuards.roleMap(MAP).check(AccessRequest.inbound("alice", "order:cancel", "order-42"));
        assertTrue(d.isAllow());
    }

    @Test
    void 角色不匹配拒绝_理由教学化() {
        Decision d = AccessGuards.roleMap(MAP).check(AccessRequest.inbound("bob", "order:cancel", "order-42"));
        assertTrue(d.isDeny());
        assertTrue(d.reason().contains("member"), "拒绝理由必须说明主体现有角色：" + d.reason());
        assertTrue(d.reason().contains("admin"), "拒绝理由必须说明所需角色：" + d.reason());
    }

    @Test
    void 未配置动作默认拒绝_理由给出修复路径() {
        Decision d = AccessGuards.roleMap(MAP).check(AccessRequest.inbound("alice", "order:export", "-"));
        assertTrue(d.isDeny());
        assertTrue(d.reason().contains("allowsRoles"), "拒绝理由必须教人怎么配：" + d.reason());
    }

    @Test
    void 预算限额内放行_耗尽否决并携带重试语义() {
        var budget = AccessGuards.budget("ai-calls", BudgetSpec.of(2, Duration.ofSeconds(30)));
        assertTrue(budget.check(AccessRequest.inbound("alice", "tool:search", "-")).isAllow());
        assertTrue(budget.check(AccessRequest.inbound("alice", "tool:search", "-")).isAllow());
        Decision denied = budget.check(AccessRequest.inbound("alice", "tool:search", "-"));
        assertTrue(denied.isDeny());
        assertEquals(new Retry.Later(Duration.ofSeconds(30)), denied.retry());
        assertTrue(denied.reason().contains("ai-calls"), "否决理由必须点名是哪个预算：" + denied.reason());
    }

    @Test
    void 与判定链组合_fail_fast权限先拦() {
        var chain = cn.youhuale.leitu.core.guard.api.GuardChain.of(
                AccessGuards.roleMap(MAP),
                AccessGuards.budget("ai-calls", BudgetSpec.of(1, Duration.ofSeconds(30))));
        // bob 权限不过 → 预算未被消耗（fail-fast），配额留给下一个人
        assertTrue(chain.check(AccessRequest.inbound("bob", "order:cancel", "-")).isDeny());
        assertTrue(chain.check(AccessRequest.inbound("alice", "order:cancel", "-")).isAllow(),
                "bob 的否决发生在预算扣减之前——fail-fast 保护了配额");
    }

    @Test
    void 规格参数错误即教学() {
        assertThrows(IllegalArgumentException.class, () -> BudgetSpec.of(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> BudgetSpec.of(1, Duration.ZERO));
    }
}
