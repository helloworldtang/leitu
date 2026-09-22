package cn.youhuale.leitu.capability.access;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

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

    /** 可拨时钟：窗口按它推进，测试不必真等一个窗口。 */
    static final class TickingClock extends Clock {
        private volatile Instant now;

        TickingClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /**
     * 反向自测：计数必须真的能回零。
     * 若有人把窗口重置逻辑删掉（回到只增不减的 AtomicLong），最后一条断言立刻变红——
     * 那时 Retry.later 就是一句空话：客户端照着"稍后重试"再来，服务端永久拒绝。
     */
    @Test
    void 预算窗口耗尽后_跨过窗口即重置() {
        TickingClock clock = new TickingClock(Instant.parse("2026-01-01T00:00:00Z"));
        var budget = AccessGuards.budget("ai-calls", BudgetSpec.of(2, Duration.ofSeconds(30)), clock);
        var req = AccessRequest.inbound("alice", "tool:search", "-");

        assertTrue(budget.check(req).isAllow());
        assertTrue(budget.check(req).isAllow());
        assertTrue(budget.check(req).isDeny(), "窗口内第三次应被否决");

        clock.advance(Duration.ofSeconds(31));
        assertTrue(budget.check(req).isAllow(), "跨过窗口后计数回零——Retry.later 的承诺要能兑现");
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
