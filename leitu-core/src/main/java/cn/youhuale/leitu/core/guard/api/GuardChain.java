package cn.youhuale.leitu.core.guard.api;

import cn.youhuale.leitu.core.guard.internal.DefaultGuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.spi.Guard;

import java.util.List;

/**
 * 判定链——"这次访问（或出站调用）允许吗？"的标准答案。
 *
 * <p>一串可插拔 {@link Guard}，任一可否决；首个否决即返回（fail-fast，见问题页"取舍"）。
 * 未装配任何 Guard 时按安全默认拒绝（deny-by-default）。
 *
 * <p>两个执行点，同一协议：入口判定链（INBOUND）/ 出站判定链（OUTBOUND），方向由
 * {@link AccessRequest#direction()} 携带，Guard 自行决定是否关注。
 *
 * <p>用法：
 * <pre>{@code
 * GuardChain chain = GuardChain.of(roleGuard, rateLimitGuard);
 * Decision d = chain.check(AccessRequest.inbound("user-1", "order:cancel-own", "order-42"));
 * if (d.isDeny()) {
 *     throw new AccessDeniedException(d);  // reason 已含"为什么"，retry 已含"怎么办"
 * }
 * }</pre>
 */
public interface GuardChain {

    /** 执行判定链。永不返回 null。 */
    Decision check(AccessRequest request);

    /**
     * 装配判定链。空数组同样得到一条可用的链——它对一切请求返回安全默认拒绝。
     */
    static GuardChain of(Guard... guards) {
        return new DefaultGuardChain(List.of(guards));
    }
}
