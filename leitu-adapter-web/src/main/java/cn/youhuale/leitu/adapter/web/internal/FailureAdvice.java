package cn.youhuale.leitu.adapter.web.internal;

import cn.youhuale.leitu.adapter.web.FailureNoticeException;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 失败交代的 HTTP 投影（内部）：{@link FailureNoticeException}（含判定链否决）→ problem+json；
 * 未捕获异常 → 系统错脱敏交代（traceId 随行）；框架异常（ErrorResponse）保留其状态与说明。
 *
 * <p>只做交代通道，不做统一响应包装——非业务端点豁免见 {@code FrameworkEndpoints}。
 * 排序为最低优先级：使用方自己的 advice 可先于本投影处理。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class FailureAdvice {

    public static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    private final ExecutionContextReader who;

    public FailureAdvice() {
        this(ExecutionContextReader.threadLocal());
    }

    public FailureAdvice(ExecutionContextReader who) {
        this.who = who;
    }

    @ExceptionHandler(FailureNoticeException.class)
    public ResponseEntity<Map<String, Object>> onFailureNotice(FailureNoticeException ex) {
        return respond(ex.notice());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> onOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            // 框架异常：保留其语义（状态码与说明），只统一媒体类型
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .contentType(PROBLEM_JSON)
                    .body(errorResponse.getBody());
        }
        return respond(FailureNotice.fromThrowable(ex, who.current()));
    }

    private ResponseEntity<Map<String, Object>> respond(FailureNotice notice) {
        int status = ProblemJson.statusOf(notice);
        HttpHeaders headers = new HttpHeaders();
        ProblemJson.applyHeaders(headers, notice);
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(PROBLEM_JSON)
                .body(ProblemJson.bodyOf(notice, status));
    }
}
