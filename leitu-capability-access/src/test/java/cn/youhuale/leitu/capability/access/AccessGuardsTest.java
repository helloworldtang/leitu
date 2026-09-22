package cn.youhuale.leitu.capability.access;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.spi.Guard;
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

    /**
     * 反向自测（#15）：匿名不参与判定——哪怕有人给 anonymous 配了 admin。
     *
     * <p>过去 anonymous 的 kind 是 HUMAN，于是它跟真人一样走进角色匹配：
     * 只要在角色表里给 "anonymous" 配了角色（或按主体名匹配时恰好放行），
     * 一条未认证请求就拿到了管理员权限，而配置看起来毫无异常。
     *
     * <p>先验拒绝放在判定链上而非某个 Guard 里：放回 Guard 里的话，
     * 用户自己写的 Guard（例如 {@code !"alice".equals(subject) ? deny : allow}）
     * 依旧会把匿名放过去——收口必须在所有 Guard 之前。
     */
    @Test
    void 匿名不参与判定_配了角色也拒绝() {
        RoleMap withAnonymousAdmin = RoleMap.create()
                .subject("anonymous").hasRoles("admin")
                .action("order:cancel").allowsRoles("admin")
                .build();

        Decision d = GuardChain.of(AccessGuards.roleMap(withAnonymousAdmin))
                .check(AccessRequest.inbound(Operator.anonymous(), "order:cancel", "order-42"));

        assertTrue(d.isDeny(), "匿名必须被先验拒绝，与角色配置无关");
        assertTrue(d.reason().contains("匿名"), "理由要说明为什么拒：" + d.reason());
        assertTrue(d.reason().contains("认证"), "理由要指向修复方向（先认证）：" + d.reason());
    }

    /** 自定义 Guard 同样被先验覆盖：它只认 subject 字符串，本会把 anonymous 当普通主体处理。 */
    @Test
    void 自定义Guard也拦得住匿名() {
        Guard customGuard = request -> "alice".equals(request.subject())
                ? Decision.allow() : Decision.deny("仅 alice 放行（示例自定义 Guard）");
        Decision d = GuardChain.of(customGuard)
                .check(AccessRequest.inbound(Operator.anonymous(), "order:cancel", "order-42"));
        assertTrue(d.isDeny());
        assertTrue(d.reason().contains("匿名"), "拒绝必须来自链条先验，而不是某个 Guard 的巧合：" + d.reason());
    }

    /** 真人不因匿名判定受牵连：同名用户 anonymous 是另一个主体，且类型是 HUMAN。 */
    @Test
    void 真人不受匿名判定影响() {
        RoleMap map = RoleMap.create()
                .subject("anonymous").hasRoles("admin")
                .action("order:cancel").allowsRoles("admin")
                .build();
        Operator personNamedAnonymous = Operator.human("anonymous", "tenant-a");
        assertFalse(personNamedAnonymous.isAnonymous(), "真人有名字就叫 anonymous 也算人，按类型判不按名字猜");
        assertTrue(GuardChain.of(AccessGuards.roleMap(map))
                .check(AccessRequest.inbound(personNamedAnonymous, "order:cancel", "order-42")).isAllow());
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
