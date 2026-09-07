package cn.youhuale.leitu.examples.access;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定 access 能力的标准用法语义。capability 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    static RoleMap map() {
        return RoleMap.create()
                .subject("alice").hasRoles("admin")
                .subject("bob").hasRoles("member")
                .action("tool:web-search").allowsRoles("admin", "member")
                .action("tool:deploy").allowsRoles("admin")
                .build();
    }

    @Test
    void 声明式一张表_角色命中放行() {
        GuardChain chain = GuardChain.of(AccessGuards.roleMap(map()));
        assertTrue(chain.check(AccessRequest.inbound("bob", "tool:web-search", "-")).isAllow());
        assertTrue(chain.check(AccessRequest.inbound("alice", "tool:deploy", "-")).isAllow());
    }

    @Test
    void 角色不足拒绝_理由含两侧角色() {
        Decision d = GuardChain.of(AccessGuards.roleMap(map()))
                .check(AccessRequest.inbound("bob", "tool:deploy", "-"));
        assertTrue(d.isDeny());
        assertTrue(d.reason().contains("member") && d.reason().contains("admin"));
    }

    @Test
    void 预算耗尽携带重试语义() {
        GuardChain chain = GuardChain.of(
                AccessGuards.roleMap(map()),
                AccessGuards.budget("ai-calls", BudgetSpec.of(2, Duration.ofSeconds(60))));
        assertTrue(chain.check(AccessRequest.inbound("bob", "tool:web-search", "-")).isAllow());
        assertTrue(chain.check(AccessRequest.inbound("bob", "tool:web-search", "-")).isAllow());
        Decision d = chain.check(AccessRequest.inbound("bob", "tool:web-search", "-"));
        assertTrue(d.isDeny());
        assertInstanceOf(Retry.Later.class, d.retry());
    }

    @Test
    void 权限否决不消耗预算_fail_fast保护配额() {
        GuardChain chain = GuardChain.of(
                AccessGuards.roleMap(map()),
                AccessGuards.budget("ai-calls", BudgetSpec.of(1, Duration.ofSeconds(60))));
        assertTrue(chain.check(AccessRequest.inbound("bob", "tool:deploy", "-")).isDeny(), "bob 无 admin 角色");
        assertTrue(chain.check(AccessRequest.inbound("alice", "tool:deploy", "-")).isAllow(),
                "bob 的否决先于预算扣减——配额未被消耗");
    }
}
