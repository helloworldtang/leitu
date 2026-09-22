package cn.youhuale.leitu.core.guard.model;

import java.util.Objects;

/**
 * 判定结果：结论 + 为什么 + 怎么办。
 *
 * <p>否决的 reason 必须可行动（错误即教程）：说明否决依据，必要时配合 {@link Retry} 告知重试路径。
 */
public record Decision(Verdict verdict, String reason, Retry retry) {

    public Decision {
        Objects.requireNonNull(verdict, "verdict 必填");
        Objects.requireNonNull(reason, "reason 必填——判定必须说明依据（错误即教程）");
        Objects.requireNonNull(retry, "retry 必填，不重试用 Retry.never()");
        if (verdict == Verdict.ALLOW && !(retry instanceof Retry.Never)) {
            throw new IllegalArgumentException("允许的判定不携带重试语义：retry 请用 Retry.never()");
        }
    }

    /**
     * 不反对。
     *
     * <p><b>注意语义</b>：在判定链里，单个 Guard 返回 allow 是"我不否决"，<b>不是"我授权"</b>——
     * 最终结论由链条决定：任一 Guard 否决即否决，空链则 deny-by-default（见 DefaultGuardChain）。
     * 把它读成"允许"会得到错误的安全感：写了 allow 的 Guard 并不承担放行责任，
     * 责任在整条链；反之，想让某类请求通过，要的是"没有任何一条 Guard 否决"。
     */
    public static Decision allow() {
        return new Decision(Verdict.ALLOW, "允许", Retry.never());
    }

    /** 永久否决。reason 写清楚依据与出路。 */
    public static Decision deny(String reason) {
        return new Decision(Verdict.DENY, reason, Retry.never());
    }

    /** 否决，稍后可重试（限流/熔断类）。 */
    public static Decision deny(String reason, Retry retry) {
        return new Decision(Verdict.DENY, reason, retry);
    }

    public boolean isAllow() {
        return verdict == Verdict.ALLOW;
    }

    public boolean isDeny() {
        return verdict == Verdict.DENY;
    }

    @Override
    public String toString() {
        return verdict + "（" + reason + "；" + retry + "）";
    }
}
