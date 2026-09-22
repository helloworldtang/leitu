package cn.youhuale.leitu.core.guard.internal;

import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.spi.Guard;

import java.util.List;
import java.util.Objects;

/** 默认判定链实现。外部一律经 {@link GuardChain#of(Guard...)} 获取，不直接实例化本类。 */
public final class DefaultGuardChain implements GuardChain {

    private static final Decision ANONYMOUS_DENIED = Decision.deny(
            "匿名主体不参与判定：匿名是「未识别的调用方」，不是人——"
                    + "授权的前提是识别，先认证再谈放行（见 catalog 条目 identity）。"
                    + "修复：在接入口完成认证后用已认证主体构造 Operator；"
                    + "判定要看得见调用方类型，请用 AccessRequest.inbound(operator, ...) 带上操作者全貌");

    private static final Decision DENY_BY_DEFAULT = Decision.deny(
            "未装配任何 Guard：按安全默认拒绝（deny-by-default）。"
                    + "如需放行，请用 GuardChain.of(...) 装配至少一条 Guard。");

    private final List<Guard> guards;

    public DefaultGuardChain(List<Guard> guards) {
        this.guards = List.copyOf(guards);
    }

    @Override
    public Decision check(AccessRequest request) {
        // 先验：匿名不进入判定。授权的前提是识别——"未识别的调用方有没有某个权限"
        // 这个问法本身不成立，交给各 Guard 回答只会得到五花八门的答案
        // （自定义 Guard 里写一句 !"alice".equals(subject) 就把匿名放过去了）。
        // 收口在链条上：一次拒绝，覆盖所有 Guard，包括用户自己写的那些。
        if (request.anonymous()) {
            return ANONYMOUS_DENIED;
        }
        for (Guard guard : guards) {
            Decision decision = guard.check(request);
            if (decision == null) {
                throw new IllegalStateException("Guard 返回了 null 判定，链装配有误。"
                        + "修复方式：让该 Guard 对一切输入返回 Decision（否决也要返回，而不是 null）——"
                        + "判定协议见 docs/problems/allow-or-not.md。");
            }
            if (decision.isDeny()) {
                return decision;
            }
        }
        if (guards.isEmpty()) {
            return DENY_BY_DEFAULT;
        }
        return Decision.allow();
    }

    @Override
    public String toString() {
        return "GuardChain{" + guards.size() + " guards}";
    }
}
