package cn.youhuale.leitu.adapter.web;

import cn.youhuale.leitu.adapter.web.internal.FailureAdvice;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.failure.model.Kind;
import cn.youhuale.leitu.core.guard.api.AccessDeniedException;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 金测试：失败交代的 HTTP 投影（problem+json / RFC 9457）。
 * core / adapter 的任何改动让这里变红，即破坏了既有答案。
 */
class FailureAdviceTest {

    @RestController
    static class DemoController {

        @GetMapping("/business")
        String business() {
            throw new FailureNoticeException(FailureNotice.business(
                    "order.not-found", "订单不存在", "订单 ord-9527 不存在或已删除；请确认订单号后重试",
                    ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-100")));
        }

        @GetMapping("/denied")
        String denied() {
            throw FailureNoticeException.from(
                    Decision.deny("预算耗尽，稍后重试", Retry.later(Duration.ofSeconds(30))),
                    ExecutionContextReader.threadLocal());
        }

        /** core 的判定链否决载体（存取缝装饰器抛出）——投影必须兜住，不必业务翻译。 */
        @GetMapping("/data-denied")
        String dataDenied() {
            throw AccessDeniedException.from(Decision.deny("无权写 note/n-1", Retry.never()),
                    ExecutionContextReader.threadLocal());
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("jdbc:mysql://prod-db:3306/order?user=root");
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这条路");
        }

        /** http.status 写成了非数字——不该让整条交代通道跟着崩。 */
        @GetMapping("/bad-status")
        String badStatus() {
            throw new FailureNoticeException(new FailureNotice(Kind.BUSINESS, "order.bad-status",
                    "状态覆盖写错", "这个失败的 http.status 属性不是数字",
                    Retry.never(), "trace-bad", Map.of("http.status", "not-a-number")));
        }
    }

    private final List<ObservationEvent> events = new ArrayList<>();

    /** 读取器与记录器都显式注入——装配面怎么接线，这里就怎么接（不再有无参捷径可回落）。 */
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DemoController())
                .setControllerAdvice(new FailureAdvice(ExecutionContextReader.threadLocal(), events::add))
                .build();
    }

    @Test
    void 业务错_全量交代_problem_json() throws Exception {
        mvc().perform(get("/business"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:order.not-found"))
                .andExpect(jsonPath("$.title").value("订单不存在"))
                .andExpect(jsonPath("$.detail").value("订单 ord-9527 不存在或已删除；请确认订单号后重试"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.kind").value("BUSINESS"))
                .andExpect(jsonPath("$.traceId").value("trace-100"))
                .andExpect(header().string("X-Trace-Id", "trace-100"));
    }

    @Test
    void 判定否决_403_retry语义随行() throws Exception {
        try (var scope = ExecutionContextBinders.threadLocal().bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-deny"))) {
            mvc().perform(get("/denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.type").value("urn:leitu:problem:guard.denied"))
                    .andExpect(jsonPath("$.retry.mode").value("later"))
                    .andExpect(jsonPath("$.retry.afterSeconds").value(30))
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(header().string("X-Trace-Id", "trace-deny"));
        }
    }

    /**
     * 数据权限在存取缝被否决（{@code DataStores.guarded} 抛的 core 载体）→ 403 problem+json。
     * 这条让"数据权限收口"在 HTTP 侧闭环：业务代码不翻译，投影兜住。
     */
    @Test
    void 存取缝否决_403_无需业务翻译() throws Exception {
        try (var scope = ExecutionContextBinders.threadLocal().bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-guard"))) {
            mvc().perform(get("/data-denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.type").value("urn:leitu:problem:guard.denied"))
                    .andExpect(jsonPath("$.detail").value("无权写 note/n-1"))
                    .andExpect(jsonPath("$.retry.mode").value("never"))
                    .andExpect(header().string("X-Trace-Id", "trace-guard"));
        }
    }

    @Test
    void 系统错_脱敏_细节不出端() throws Exception {
        try (var scope = ExecutionContextBinders.threadLocal().bind(
                ExecutionContext.of(Operator.system(), "trace-sys"))) {
            mvc().perform(get("/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.kind").value("SYSTEM"))
                    .andExpect(jsonPath("$.type").value("urn:leitu:problem:" + FailureNotice.SYSTEM_TYPE))
                    .andExpect(jsonPath("$.detail").value(FailureNotice.SYSTEM_DETAIL))
                    .andExpect(jsonPath("$.traceId").value("trace-sys"));
        }
    }

    @Test
    void 框架异常_保留状态与说明() throws Exception {
        mvc().perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    /**
     * 反向自测：一个写错的 {@code http.status} 属性，不能让整条交代通道退化。
     * 若有人把降级处理去掉（回到裸 parseInt），异常逃出 {@code @ExceptionHandler}，
     * 响应会变成 Spring 默认错误页——下面两条断言（problem+json、traceId 头）立刻变红。
     */
    @Test
    void http_status非法_降级500_但交代通道与traceId不丢() throws Exception {
        mvc().perform(get("/bad-status"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:order.bad-status"))
                .andExpect(header().string("X-Trace-Id", "trace-bad"));
    }

    /**
     * 反向自测：异常路径必须有服务端留痕。
     * 若有人把投影里的观测记录删掉，这条立刻变红——未捕获异常将既不在交代里、也不在观测里，
     * 调用方只见一个 traceId，运维什么都查不到。
     */
    @Test
    void 系统错_观测留痕_且内部细节不出端() throws Exception {
        try (var scope = ExecutionContextBinders.threadLocal().bind(
                ExecutionContext.of(Operator.system(), "trace-obs"))) {
            String body = mvc().perform(get("/boom"))
                    .andExpect(status().isInternalServerError())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as("异常里的连接串绝不能进响应体").doesNotContain("prod-db");
        }
        assertThat(events).anySatisfy(e -> {
            assertThat(e.name()).isEqualTo(FailureAdvice.EVENT_FAILURE_PROJECTED);
            assertThat(e.outcome()).isEqualTo(Outcome.failure(FailureNotice.SYSTEM_TYPE));
            assertThat(e.attributes())
                    .containsEntry("failure.exception", IllegalStateException.class.getName())
                    .containsEntry("failure.kind", "SYSTEM");
        });
        assertThat(events).as("异常消息进观测（排障用），不进交代")
                .anySatisfy(e -> assertThat(e.attributes()).containsEntry(
                        "failure.message", "jdbc:mysql://prod-db:3306/order?user=root"));
    }
}
