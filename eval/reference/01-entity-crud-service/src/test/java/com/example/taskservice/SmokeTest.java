package com.example.taskservice;

import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
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

/** 探针冒烟：引依赖即用 + 端到端判定/读写/失败投影。 */
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
        assertThat(ctx.getBean(JdbcDataStoreFactory.class)).isNotNull();
        assertThat(ctx.getBean(OpenAPI.class)).isNotNull();
    }

    @Test
    void 创建任务_盖章_读回() throws Exception {
        mvc.perform(post("/tasks").param("id", "t-1").param("title", "核对 leitu 落地")
                        .header("X-Probe-User", "boss").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t-1"))
                .andExpect(jsonPath("$.auditFields.createdBy").value("boss"));

        mvc.perform(get("/tasks/t-1")
                        .header("X-Probe-User", "boss").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("核对 leitu 落地"));
    }

    @Test
    void 判定否决_403_problem_json() throws Exception {
        mvc.perform(post("/tasks").param("id", "t-2").param("title", "越权尝试")
                        .header("X-Probe-User", "dev").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("guard.denied"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 租户隔离_读不到_交代业务错() throws Exception {
        mvc.perform(post("/tasks").param("id", "t-3").param("title", "跨租户核对")
                        .header("X-Probe-User", "boss").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk());
        mvc.perform(get("/tasks/t-3")
                        .header("X-Probe-User", "mallory").header("X-Probe-Tenant", "tenant-b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("task.not-found"));
    }

    @Test
    void 文档端点_引依赖即得() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }
}
