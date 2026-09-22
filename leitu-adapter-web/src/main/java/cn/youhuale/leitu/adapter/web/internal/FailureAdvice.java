package cn.youhuale.leitu.adapter.web.internal;

import cn.youhuale.leitu.adapter.web.FailureNoticeException;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.api.AccessDeniedException;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 失败交代的 HTTP 投影（内部）：{@link FailureNoticeException}（含判定链否决）→ problem+json；
 * 未捕获异常 → 系统错脱敏交代（traceId 随行）；框架异常（ErrorResponse）保留其状态与说明。
 *
 * <p>只做交代通道，不做统一响应包装——非业务端点豁免见 {@code FrameworkEndpoints}。
 * 排序为最低优先级：使用方自己的 advice 可先于本投影处理。
 *
 * <p><b>交代通道之外，本投影同时是观测的兜底落点</b>：每次投影记一条 {@code failure.projected}
 * 事件，异常类名等全量细节只进观测通道、不进交代（ADR-011 的脱敏分工）。没有这一条，
 * 未捕获异常就既不在交代里、也不在观测里——调用方只见一个 traceId，服务端零留痕。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class FailureAdvice {

    public static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    /** 投影事件名（点分命名，检索键）。 */
    public static final String EVENT_FAILURE_PROJECTED = "failure.projected";

    /** 状态码解析失败时的兜底——交代通道本身不能跟着一起崩。 */
    private static final int FALLBACK_STATUS = 500;

    private final ExecutionContextReader who;

    private final ObservationRecorder recorder;

    /** 手工装配：读取器必填，观测落日志级默认（分维度默认值：观测=最小可用实现，非 Noop）。 */
    public FailureAdvice(ExecutionContextReader who) {
        this(who, ObservationRecorder.of());
    }

    /** 完整装配：读取器与记录器均由容器注入——记录器取容器 Bean，异常才进得与应用同一条观测链路。 */
    public FailureAdvice(ExecutionContextReader who, ObservationRecorder recorder) {
        this.who = who;
        this.recorder = recorder;
    }

    @ExceptionHandler(FailureNoticeException.class)
    public ResponseEntity<Map<String, Object>> onFailureNotice(FailureNoticeException ex) {
        return respond(ex.notice(), ex);
    }

    /**
     * 判定链否决（core 载体，传输无关）→ 403 problem+json，retry 语义随行（Retry-After 头）。
     *
     * <p>这一条让"数据权限收口到存取缝"在 HTTP 侧闭环：{@code DataStores.guarded(...)} 抛出的
     * 否决不必再由业务代码翻译成 HTTP 异常，投影即兜住。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onAccessDenied(AccessDeniedException ex) {
        return respond(FailureNotice.fromDecision(ex.decision(), ex.context()), ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> onOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            // 框架异常：保留其语义（状态码与说明），只统一媒体类型
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .contentType(PROBLEM_JSON)
                    .body(errorResponse.getBody());
        }
        return respond(FailureNotice.fromThrowable(ex, who.current()), ex);
    }

    private ResponseEntity<Map<String, Object>> respond(FailureNotice notice, Throwable cause) {
        int status = statusOrFallback(notice);
        HttpHeaders headers = new HttpHeaders();
        ProblemJson.applyHeaders(headers, notice);
        recordFailure(notice, cause, status);
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(PROBLEM_JSON)
                .body(ProblemJson.bodyOf(notice, status));
    }

    /**
     * 状态码解析失败不放大：一个写错的 {@code http.status} 属性不该让整条交代通道退化
     * （那样 traceId 头与 problem+json 会一起丢失，调用方只剩一个无法归因的错误页）。
     * 降级为 500 继续交代，原因记进观测。
     */
    private int statusOrFallback(FailureNotice notice) {
        try {
            return ProblemJson.statusOf(notice);
        } catch (RuntimeException e) {
            recorder.record(new ObservationEvent(EVENT_FAILURE_PROJECTED, who.current(), Instant.now(),
                    Outcome.failure("http.status 非法，状态码降级为 " + FALLBACK_STATUS), null, null,
                    Map.of("failure.type", notice.type(),
                            "failure.status.invalid", String.valueOf(e.getMessage()))));
            return FALLBACK_STATUS;
        }
    }

    /**
     * 全量细节走观测通道：异常类名 / 失败分类 / 状态码只进事件，不进交代（脱敏分工见 ADR-011）。
     * 观测自身的异常由 {@link ObservationRecorder} 吞掉——投影失败不能盖掉原始的失败交代。
     */
    private void recordFailure(FailureNotice notice, Throwable cause, int status) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("failure.type", notice.type());
        attributes.put("failure.kind", notice.kind().name());
        attributes.put("failure.status", String.valueOf(status));
        if (cause != null) {
            attributes.put("failure.exception", cause.getClass().getName());
            String message = cause.getMessage();
            if (message != null && !message.isBlank()) {
                attributes.put("failure.message", message);
            }
        }
        recorder.record(new ObservationEvent(EVENT_FAILURE_PROJECTED, who.current(), Instant.now(),
                Outcome.failure(notice.type()), null, null, attributes));
    }
}
