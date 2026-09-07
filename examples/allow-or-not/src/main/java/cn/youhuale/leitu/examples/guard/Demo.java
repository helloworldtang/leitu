package cn.youhuale.leitu.examples.guard;

import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;

import java.time.Duration;
import java.util.Set;

/**
 * 金样本：判定链的最小可运行示范。
 *
 * <p>照着这个样子写你的 Guard 与链——这是"允许吗"的标准答案用法（docs/problems/allow-or-not.md）。
 */
public final class Demo {

    /** 示例 Guard 一：只有管理员能取消订单（权限类，永久否决）。 */
    static final class OwnerRoleGuard implements cn.youhuale.leitu.core.guard.spi.Guard {
        private final Set<String> admins;

        OwnerRoleGuard(Set<String> admins) {
            this.admins = admins;
        }

        @Override
        public Decision check(AccessRequest request) {
            if (request.action().startsWith("order:") && !admins.contains(request.subject())) {
                return Decision.deny("仅管理员可执行 " + request.action()
                        + "；当前操作者 " + request.subject() + " 不在名单内");
            }
            return Decision.allow();
        }
    }

    /** 示例 Guard 二：每分钟最多放行 3 次（限流类，稍后重试）。 */
    static final class FixedRateGuard implements cn.youhuale.leitu.core.guard.spi.Guard {
        private int remaining;

        FixedRateGuard(int limit) {
            this.remaining = limit;
        }

        @Override
        public synchronized Decision check(AccessRequest request) {
            if (remaining <= 0) {
                return Decision.deny("请求过密：当前窗口额度已用尽", Retry.later(Duration.ofSeconds(60)));
            }
            remaining--;
            return Decision.allow();
        }
    }

    public static void main(String[] args) {
        GuardChain chain = GuardChain.of(
                new OwnerRoleGuard(Set.of("alice")),
                new FixedRateGuard(3));

        // 1) 管理员、额度内 → 允许
        System.out.println(chain.check(AccessRequest.inbound("alice", "order:cancel-own", "order-42")));
        // 2) 非管理员 → 永久否决（fail-fast，限流 Guard 不会被执行）
        System.out.println(chain.check(AccessRequest.inbound("bob", "order:cancel-own", "order-42")));
        // 3) 管理员、额度耗尽 → 稍后重试
        for (int i = 0; i < 3; i++) {
            chain.check(AccessRequest.inbound("alice", "order:query", "-"));
        }
        System.out.println(chain.check(AccessRequest.inbound("alice", "order:query", "-")));
        // 4) 什么都没装配 → 安全默认拒绝
        System.out.println(GuardChain.of().check(AccessRequest.inbound("bob", "order:query", "-")));
    }
}
