package cn.youhuale.leitu.examples.guard;

import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 金测试：锁定判定链的标准用法语义。core 的任何改动让这里变红，即破坏了既有答案。
 */
class GoldenTest {

    private final GuardChain chain = GuardChain.of(
            new Demo.OwnerRoleGuard(Set.of("alice")),
            new Demo.FixedRateGuard(2));

    @Test
    void 管理员_额度内_允许() {
        Decision d = chain.check(AccessRequest.inbound("alice", "order:cancel-own", "order-42"));
        assertTrue(d.isAllow());
    }

    @Test
    void 非管理员_永久否决() {
        Decision d = chain.check(AccessRequest.inbound("bob", "order:cancel-own", "order-42"));
        assertTrue(d.isDeny());
        assertTrue(d.reason().contains("仅管理员"), "否决理由必须说明依据：" + d.reason());
    }

    @Test
    void 额度耗尽_稍后重试() {
        chain.check(AccessRequest.inbound("alice", "order:query", "-"));
        chain.check(AccessRequest.inbound("alice", "order:query", "-"));
        Decision d = chain.check(AccessRequest.inbound("alice", "order:query", "-"));
        assertTrue(d.isDeny());
        assertTrue(d.retry() instanceof cn.youhuale.leitu.core.guard.model.Retry.Later,
                "限流类否决必须携带重试语义：" + d);
    }

    @Test
    void 空链_安全默认拒绝() {
        Decision d = GuardChain.of().check(AccessRequest.inbound("bob", "order:query", "-"));
        assertTrue(d.isDeny());
    }
}
