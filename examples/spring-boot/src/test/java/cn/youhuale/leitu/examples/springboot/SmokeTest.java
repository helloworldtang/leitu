package cn.youhuale.leitu.examples.springboot;

import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端冒烟金测试：引依赖即用——装配就位 / 真库读写 / 失败投影 / 文档端点。
 * starter、adapter、capability 或 core 的任何改动让这里变红，即破坏了「引依赖即用」的承诺。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SmokeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext ctx;

    @Test
    void 引依赖即用_端口与文档就位() {
        assertThat(ctx.getBean(GuardChain.class)).isNotNull();
        assertThat(ctx.getBean(ObservationRecorder.class)).isNotNull();
        assertThat(ctx.getBean(ConfigReader.class)).isNotNull();
        assertThat(ctx.getBean(CachePolicy.class)).isNotNull();
        assertThat(ctx.getBean(JdbcDataStoreFactory.class)).isNotNull();
        assertThat(ctx.getBean(OpenAPI.class)).isNotNull();
    }

    @Test
    void 创建订单_盖章_读回() throws Exception {
        mvc.perform(post("/orders").param("id", "o-1").param("amountCents", "9900")
                        .header("X-Demo-User", "alice").header("X-Demo-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("o-1"))
                .andExpect(jsonPath("$.auditFields.createdBy").value("alice"))
                .andExpect(jsonPath("$.auditFields.updatedBy").value("alice"));

        mvc.perform(get("/orders/o-1")
                        .header("X-Demo-User", "alice").header("X-Demo-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(9900));
    }

    @Test
    void 租户隔离_读不到_交代业务错() throws Exception {
        mvc.perform(post("/orders").param("id", "o-2").param("amountCents", "100")
                        .header("X-Demo-User", "alice").header("X-Demo-Tenant", "tenant-a"))
                .andExpect(status().isOk());
        mvc.perform(get("/orders/o-2")
                        .header("X-Demo-User", "mallory").header("X-Demo-Tenant", "tenant-b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:order.not-found"));
    }

    @Test
    void 判定否决_403_problem_json() throws Exception {
        mvc.perform(post("/orders").param("id", "o-3").param("amountCents", "100")
                        .header("X-Demo-User", "bob").header("X-Demo-Tenant", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:guard.denied"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 系统错_脱敏交代_traceId随行() throws Exception {
        mvc.perform(get("/demo/boom").header("X-Demo-User", "alice"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.kind").value("SYSTEM"))
                .andExpect(jsonPath("$.detail").value(FailureNotice.SYSTEM_DETAIL))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 文档端点_引依赖即得() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }
}
