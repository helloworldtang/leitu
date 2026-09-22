package cn.youhuale.leitu.adapter.web.internal;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.failure.model.Kind;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * problem+json 的 type 成员：RFC 9457 要的是 URI，不是点分字符串（#12）。
 *
 * <p>这类"格式合规性"缺陷的特征是：肉眼读 JSON 看不出问题，程序按 RFC 解析才炸——
 * 所以判据必须交给 {@link URI} 去判，而不是写在注释里。
 */
class ProblemJsonTest {

    private static FailureNotice notice(String type) {
        return new FailureNotice(Kind.BUSINESS, type, "标题", "详情",
                Retry.never(), "trace-1", java.util.Map.of());
    }

    @Test
    void type投影成URN_可被标准库解析为URI() {
        URI uri = URI.create(ProblemJson.typeUriOf(notice("order.not-found")));
        assertThat(uri.isAbsolute()).as("URN 是绝对 URI，不依赖部署路径").isTrue();
        assertThat(uri.getScheme()).isEqualTo("urn");
        assertThat(uri.getSchemeSpecificPart()).isEqualTo("leitu:problem:order.not-found");
    }

    @Test
    void 内部机读标识保留在URI末段() {
        String urn = ProblemJson.typeUriOf(notice(FailureNotice.SYSTEM_TYPE));
        assertThat(urn).endsWith(FailureNotice.SYSTEM_TYPE);
        assertThat(urn.substring(urn.lastIndexOf(':') + 1))
                .as("取末段即可回到机读标识，消费方据此分流")
                .isEqualTo("system.unexpected");
    }

    /**
     * 反向自测：若有人把 {@code bodyOf} 改回 {@code notice.type()}（裸点分字符串），
     * 这条立刻红——"对齐 RFC 9457"就会重新变成一句没有机器兜底的口号。
     */
    @Test
    void body里的type是URI_不是裸标识符() {
        FailureNotice notice = FailureNotice.business("order.not-found", "订单不存在",
                "订单 ord-1 不存在", ExecutionContext.of(Operator.system(), "trace-1"));
        Object type = ProblemJson.bodyOf(notice, 400).get("type");
        assertThat(type).isEqualTo("urn:leitu:problem:order.not-found");
        assertThat(URI.create(String.valueOf(type)).getScheme()).isEqualTo("urn");
    }
}
