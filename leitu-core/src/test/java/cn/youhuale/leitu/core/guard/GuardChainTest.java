package cn.youhuale.leitu.core.guard;

import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GuardChainTest {

    private static final AccessRequest REQ = AccessRequest.inbound("user-1", "order:cancel-own", "order-42");

    @Test
    void 空链按安全默认拒绝_deny_by_default() {
        Decision d = GuardChain.of().check(REQ);
        assertTrue(d.isDeny());
        assertTrue(d.reason().contains("deny-by-default"), "拒绝理由必须教人怎么修：" + d.reason());
        assertTrue(d.reason().contains("GuardChain.of"), "拒绝理由必须给出出路：" + d.reason());
    }

    @Test
    void 全部允许则允许() {
        Decision d = GuardChain.of(req -> Decision.allow(), req -> Decision.allow()).check(REQ);
        assertTrue(d.isAllow());
    }

    @Test
    void 首个否决即返回_fail_fast_后续不再执行() {
        AtomicInteger laterCalls = new AtomicInteger();
        Decision d = GuardChain.of(
                req -> Decision.deny("角色无权取消他人订单"),
                req -> {
                    laterCalls.incrementAndGet();
                    return Decision.allow();
                }
        ).check(REQ);
        assertTrue(d.isDeny());
        assertEquals("角色无权取消他人订单", d.reason());
        assertEquals(0, laterCalls.get(), "首个否决后链即返回，后续 Guard 不执行");
    }

    @Test
    void 否决可携带重试语义() {
        Decision d = GuardChain.of(
                req -> Decision.deny("请求过密", Retry.later(Duration.ofSeconds(30)))
        ).check(REQ);
        assertEquals(new Retry.Later(Duration.ofSeconds(30)), d.retry());
    }

    @Test
    void Guard返回null视为装配错误_异常信息含修复方式() {
        GuardChain bad = GuardChain.of(req -> null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> bad.check(REQ));
        assertTrue(e.getMessage().contains("修复方式"), "异常必须即教程：" + e.getMessage());
    }

    @Test
    void 允许的判定不携带重试语义_构造即校验() {
        assertThrows(IllegalArgumentException.class,
                () -> new Decision(cn.youhuale.leitu.core.guard.model.Verdict.ALLOW, "允许",
                        Retry.later(Duration.ofSeconds(1))));
    }

    @Test
    void 重试时长必须为正() {
        assertThrows(IllegalArgumentException.class, () -> Retry.later(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Retry.later(Duration.ofSeconds(-1)));
    }

    /**
     * 反向自测（#15）：判定请求要能携带操作者全貌，判定层才看得见"匿名"，
     * 不用各自去比对 "anonymous" 字面量。
     */
    @Test
    void 请求携带操作者全貌_匿名可见() {
        AccessRequest anon = AccessRequest.inbound(Operator.anonymous(), "order:cancel", "order-42");
        assertTrue(anon.anonymous());
        assertEquals("anonymous", anon.subject(), "subject 仍从操作者取，判定层无需改读法");

        AccessRequest human = AccessRequest.inbound(Operator.human("alice", "tenant-a"), "order:cancel", "order-42");
        assertFalse(human.anonymous());
        assertEquals("tenant-a", human.operator().tenant(), "带上全貌后租户也可得（判定按租户放行有了前提）");
    }

    /** 未携带操作者时不替判定层猜——没有信息就是没有信息。 */
    @Test
    void 未携带操作者时不猜匿名() {
        assertFalse(AccessRequest.inbound("anonymous", "order:cancel", "order-42").anonymous(),
                "只给 subject 时无从判断类型：猜成匿名会把真人误拒，猜成非匿名又漏放——都不猜");
        assertNull(AccessRequest.inbound("alice", "order:cancel", "order-42").operator());
    }

    /**
     * 反向自测（#16）：判定要看租户，前提是请求带得动租户。
     *
     * <p>旧形态只有 subject 字符串：判定层看不见租户，于是"这个资源是不是本租户的"没法回答，
     * 实际效果是默认放行跨租户访问（同名 subject 在另一个租户里是另一个人）。
     * 这里用一条只认 tenant-a 的 Guard 演示：tenant-b 的 alice 必须被拒，
     * 且拒绝理由要说出双方租户——"彼此都叫 alice"不该成为越权的理由。
     */
    @Test
    void Guard按租户判定_同名异租户即否决() {
        // 资源约定：order-42 属 tenant-a（业务侧的元数据，此处写死只为演示判定缝的用法）
        var chain = GuardChain.of(request -> request.tenant()
                .filter("tenant-a"::equals)
                .map(t -> Decision.allow())
                .orElseGet(() -> Decision.deny("跨租户访问被拒：资源 order-42 属 tenant-a，"
                        + "本请求租户=" + request.tenant().orElse("未知（请求未携带操作者全貌）"))));

        AccessRequest sameTenant =
                AccessRequest.inbound(Operator.human("alice", "tenant-a"), "order:cancel", "order-42");
        AccessRequest otherTenant =
                AccessRequest.inbound(Operator.human("alice", "tenant-b"), "order:cancel", "order-42");

        assertTrue(chain.check(sameTenant).isAllow(), "同租户放行");
        Decision denied = chain.check(otherTenant);
        assertTrue(denied.isDeny(), "同名不同租户必须否决");
        assertTrue(denied.reason().contains("tenant-b"), "拒绝理由要说清双方租户：" + denied.reason());
    }

    @Test
    void 未携带操作者时租户不可见_判定层据此否定归属() {
        assertTrue(AccessRequest.inbound(Operator.human("alice", "tenant-a"), "order:cancel", "order-42")
                .tenant().isPresent(), "带全貌则租户可得");
        assertTrue(AccessRequest.inbound("alice", "order:cancel", "order-42").tenant().isEmpty(),
                "只有 subject 时租户为空——看不见租户不等于所有租户，判定层应当按无法归属处理");
    }

    /** {@code allow()} 的真正语义是"本 Guard 不否决"：一条 allow 撑不起放行，链条说了算。 */
    @Test
    void 单个Guard的allow不等于授权_另一条否决即否决() {
        Decision d = GuardChain.of(
                req -> Decision.allow(),
                req -> Decision.deny("频率超限", Retry.later(Duration.ofSeconds(30)))
        ).check(REQ);
        assertTrue(d.isDeny(), "写过 allow 的那条 Guard 并不承担放行责任");
        assertEquals("频率超限", d.reason());
    }

    @Test
    void 出站与入口同一协议() {
        AccessRequest out = AccessRequest.outbound("svc-order", "call:inventory", "inventory-svc");
        Decision d = GuardChain.of(req -> req.direction() == AccessRequest.Direction.OUTBOUND
                ? Decision.deny("熔断打开：inventory-svc 不可用", Retry.later(Duration.ofSeconds(10)))
                : Decision.allow()).check(out);
        assertTrue(d.isDeny());
        assertEquals(out.direction(), AccessRequest.Direction.OUTBOUND);
    }
}
