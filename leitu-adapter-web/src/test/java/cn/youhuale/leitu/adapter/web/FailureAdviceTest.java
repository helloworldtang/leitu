package cn.youhuale.leitu.adapter.web;

import cn.youhuale.leitu.adapter.web.internal.FailureAdvice;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

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

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("jdbc:mysql://prod-db:3306/order?user=root");
        }

        @GetMapping("/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有这条路");
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new DemoController())
                .setControllerAdvice(new FailureAdvice())
                .build();
    }

    @Test
    void 业务错_全量交代_problem_json() throws Exception {
        mvc().perform(get("/business"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("order.not-found"))
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
                    .andExpect(jsonPath("$.type").value("guard.denied"))
                    .andExpect(jsonPath("$.retry.mode").value("later"))
                    .andExpect(jsonPath("$.retry.afterSeconds").value(30))
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(header().string("X-Trace-Id", "trace-deny"));
        }
    }

    @Test
    void 系统错_脱敏_细节不出端() throws Exception {
        try (var scope = ExecutionContextBinders.threadLocal().bind(
                ExecutionContext.of(Operator.system(), "trace-sys"))) {
            mvc().perform(get("/boom"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.kind").value("SYSTEM"))
                    .andExpect(jsonPath("$.type").value(FailureNotice.SYSTEM_TYPE))
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
}
