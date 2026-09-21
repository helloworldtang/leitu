package cn.youhuale.leitu.adapter.web.internal;

import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.failure.model.Kind;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.springframework.http.HttpHeaders;

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
            int status = Integer.parseInt(override);
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

    static Map<String, Object> bodyOf(FailureNotice notice, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", notice.type());
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
