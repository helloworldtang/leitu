package com.example.bookcatalog;

import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.core.config.api.ConfigReader;
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

/** 探针 02 冒烟：引依赖即用 + 缓存命中证据 + 失败投影 + 文档契约。 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogAcceptanceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext ctx;

    @Autowired
    CountingDataStore countingBooks;

    private static final String ALICE = "X-Probe-User: alice";
    private static final String TENANT_A = "X-Probe-Tenant: tenant-a";
    private static final String TENANT_B = "X-Probe-Tenant: tenant-b";

    @Test
    void 引依赖即用_端口与文档就位() {
        assertThat(ctx.getBean(GuardChain.class)).isNotNull();
        assertThat(ctx.getBean(ObservationRecorder.class)).isNotNull();
        assertThat(ctx.getBean(ConfigReader.class)).isNotNull();
        assertThat(ctx.getBean(JdbcDataStoreFactory.class)).isNotNull();
        assertThat(ctx.getBean(CachePolicy.class)).isNotNull();
        assertThat(ctx.getBean(Cache.class)).isNotNull();
        assertThat(ctx.getBean(OpenAPI.class)).isNotNull();
    }

    @Test
    void 创建_审计盖章() throws Exception {
        mvc.perform(post("/books?id=b-1&title=Clean%20Code&priceCents=8800").header("X-Probe-User", "alice")
                        .header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditFields.createdBy").value("alice"))
                .andExpect(jsonPath("$.priceCents").value(8800));
    }

    @Test
    void 守卫_非授权创建403且problem加json() throws Exception {
        mvc.perform(post("/books?id=b-2&title=x&priceCents=1").header("X-Probe-User", "bob")
                        .header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:guard.denied"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 读取走缓存_同一id两次读取只触库一次() throws Exception {
        mvc.perform(post("/books?id=b-3&title=x&priceCents=2").header("X-Probe-User", "alice")
                        .header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk());
        long before = countingBooks.reads();
        mvc.perform(get("/books/b-3").header("X-Probe-User", "alice").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk());
        mvc.perform(get("/books/b-3").header("X-Probe-User", "alice").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk());
        assertThat(countingBooks.reads()).as("两次读取应只触库一次（第二次命中缓存）").isEqualTo(before + 1);
    }

    @Test
    void 缓存键租户作用域_他租户读同id不串味() throws Exception {
        mvc.perform(post("/books?id=b-4&title=x&priceCents=3").header("X-Probe-User", "alice")
                        .header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isOk());
        long before = countingBooks.reads();
        mvc.perform(get("/books/b-4").header("X-Probe-User", "alice").header("X-Probe-Tenant", "tenant-b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:book.not-found"));
        assertThat(countingBooks.reads()).as("他租户读同 id：缓存键按租户作用域，必须触库且读不到")
                .isEqualTo(before + 1);
    }

    @Test
    void 读不到_业务错problem加json() throws Exception {
        mvc.perform(get("/books/never-exists").header("X-Probe-User", "alice").header("X-Probe-Tenant", "tenant-a"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:leitu:problem:book.not-found"))
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void 文档契约_operationId与动作同源() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        assertThat(body).contains("\"book.create\"").contains("\"book.get\"");
    }
}
