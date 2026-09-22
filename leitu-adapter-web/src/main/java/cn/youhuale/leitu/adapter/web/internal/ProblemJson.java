package cn.youhuale.leitu.adapter.web.internal;

import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.failure.model.Kind;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FailureNotice → HTTP problem+json（RFC 9457）投影（内部）：
 * 组件对齐 type/title/detail/status + 扩展成员（kind/retry/traceId/attributes）。
 *
 * <p>状态码：默认 BUSINESS→400、SYSTEM→500、判定否决（guard.denied）→403；
 * 可用 {@code notice.attributes()} 的 "http.status" 显式覆盖（400–599）。
 */
final class ProblemJson {

    static final String STATUS_ATTRIBUTE = "http.status";

    private ProblemJson() {
    }

    static int statusOf(FailureNotice notice) {
        String override = notice.attributes().get(STATUS_ATTRIBUTE);
        if (override != null) {
            int status = parseStatus(override);
            if (status < 400 || status > 599) {
                throw new IllegalArgumentException("\"http.status\" 必须是 4xx/5xx（400–599）：当前 " + status);
            }
            return status;
        }
        if (notice.kind() == Kind.SYSTEM) {
            return 500;
        }
        return FailureNotice.GUARD_TYPE.equals(notice.type()) ? 403 : 400;
    }

    /**
     * 显式解析为十进制整数——不裸用 {@code Integer.parseInt}：它抛的 NumberFormatException
     * 会逃出 {@code @ExceptionHandler}，把整条交代通道打回 Spring 默认错误页
     * （traceId 头与 problem+json 一起丢失），而根因只是一个属性写错。
     * 这里统一成教学式失败，由调用方决定降级。
     */
    private static int parseStatus(String raw) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"http.status\" 必须是十进制整数（400–599）：当前 \""
                    + raw + "\"；正确示例：\"503\"", e);
        }
    }

    /**
     * 内部 type（点分机读标识）→ RFC 9457 的 {@code type} 成员：必须是 URI，这里 URN 化
     * （{@code urn:leitu:problem:order.not-found}）——见 ADR-011「type 可 URN 化」。
     *
     * <p><b>为什么不直接吐点分字符串</b>：RFC 9457 §4.2.1 规定 type 是 URI 引用。
     * "order.not-found" 作为相对引用会被解析成"当前站点根下的 order.not-found 路径"——
     * 消费方拿它去比对尚可，一旦按 URI 去抓取（抓文档是常见做法）就会跑到错的地址。
     * URN 明确表述"这是个名字，不是地址"：不承诺 dereference 会得到什么，
     * 也不因部署路径变化而改变——同一个失败在生产与预发环境是同一个 type。
     *
     * <p>末段就是机读标识本身，比对时取 {@code type.substring(type.lastIndexOf(':') + 1)} 即可。
     */
    static final String TYPE_URN_PREFIX = "urn:leitu:problem:";

    static String typeUriOf(FailureNotice notice) {
        String urn = TYPE_URN_PREFIX + notice.type();
        try {
            URI.create(urn);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type 投影出的 URI 非法（type=\"" + notice.type()
                    + "\"）：URI 安全字符集由 FailureNotice 构造期保证，走到这里说明校验被绕过了", e);
        }
        return urn;
    }

    static Map<String, Object> bodyOf(FailureNotice notice, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", typeUriOf(notice));
        body.put("title", notice.title());
        body.put("status", status);
        body.put("detail", notice.detail());
        body.put("kind", notice.kind().name());
        body.put("retry", retryOf(notice.retry()));
        body.put("traceId", notice.traceId());
        if (!notice.attributes().isEmpty()) {
            body.put("attributes", notice.attributes());
        }
        return body;
    }

    static void applyHeaders(HttpHeaders headers, FailureNotice notice) {
        headers.set("X-Trace-Id", notice.traceId());
        if (notice.retry() instanceof Retry.Later later) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, later.after().toSeconds())));
        }
    }

    private static Map<String, Object> retryOf(Retry retry) {
        if (retry instanceof Retry.Later later) {
            return Map.of("mode", "later", "afterSeconds", later.after().toSeconds());
        }
        if (retry instanceof Retry.Never) {
            return Map.of("mode", "never");
        }
        return Map.of("mode", "unknown", "text", retry.toString());
    }
}
