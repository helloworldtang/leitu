package com.example.fxservice;

import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 探针 03 冒烟：配置驱动 + 观测事件 + 失败投影 + 文档契约。 */
@SpringBootTest(properties = "fx.rate.USD-CNY=7.77")
@AutoConfigureMockMvc
class FxAcceptanceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext ctx;

    @Autowired
    CapturingSink sink;

    @TestConfiguration
    static class CaptureConfig {
        @Bean
        CapturingSink capturingSink() {
            return new CapturingSink();
        }
    }

    /** 自定义事件落点：验收 4 的证据面——观测事件能被业务自己的落点捕获。 */
    static class CapturingSink implements ObservationSink {
        final List<ObservationEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void accept(ObservationEvent event) {
            events.add(event);
        }

        List<ObservationEvent> byName(String name) {
            return events.stream().filter(e -> e.name().equals(name)).toList();
        }
    }

    private static final String ALICE = "X-Probe-User: alice";
    private static final String TENANT = "X-Probe-Tenant: tenant-a";

    @Test
    void 引依赖即用_端口就位() {
        assertThat(ctx.getBean(GuardChain.class)).isNotNull();
        assertThat(ctx.getBean(ObservationRecorder.class)).isNotNull();
        assertThat(ctx.getBean(ConfigReader.class)).isNotNull();
        assertThat(ctx.getBean(JdbcDataStoreFactory.class)).isNotNull();
        assertThat(ctx.getBean(ExecutionContextBinder.class)).isNotNull();
        assertThat(ctx.getBean(ExecutionContextReader.class)).isNotNull();
        assertThat(ctx.getBean(OpenAPI.class)).isNotNull();
    }

    @Test
    void 换算由配置驱动_测试属性覆盖classpath值() throws Exception {
        // classpath leitu.properties 写的是 7.25；测试属性 7.77 必须赢——
        // 这就是「改配置行为跟着变」的证据（源优先级由 config-source 保证）。
        mvc.perform(get("/fx/convert?from=USD&to=CNY&amount=100").header("X-Probe-User", "alice")
                        .header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(7.77))
                .andExpect(jsonPath("$.result").value(777.0));
    }

    @Test
    void 每次调用记录观测事件() throws Exception {
        int before = sink.byName("fx.converted").size();
        MvcResult result = mvc.perform(get("/fx/convert?from=USD&to=CNY&amount=50")
                        .header("X-Probe-User", "alice").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();
        List<ObservationEvent> converted = sink.byName("fx.converted");
        assertThat(converted.size()).as("调用后必须新增一条 fx.converted").isEqualTo(before + 1);
        ObservationEvent event = converted.get(converted.size() - 1);
        assertThat(event.context().operator().subject()).isEqualTo("alice");
        assertThat(event.context().traceId()).isNotBlank();
        assertThat(event.duration()).isNotNull();
        // traceId 一致：响应头、事件、上下文三者同源
        assertThat(result.getResponse().getHeader("X-Trace-Id")).isEqualTo(event.context().traceId());
    }

    @Test
    void 未知币对_业务错problem加json() throws Exception {
        mvc.perform(get("/fx/convert?from=USD&to=XYZ&amount=1")
                        .header("X-Probe-User", "alice").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:fx.pair-not-found"))
                .andExpect(header().exists("X-Trace-Id"));
        assertThat(sink.byName("fx.pair-not-found")).isNotEmpty();
    }

    @Test
    void 守卫_非授权主体403且problem加json() throws Exception {
        mvc.perform(get("/fx/convert?from=USD&to=CNY&amount=1")
                        .header("X-Probe-User", "mallory").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:guard.denied"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 文档契约_operationId在文档中() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(body).contains("\"fx.convert\"");
    }
}
