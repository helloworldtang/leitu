package cn.youhuale.leitu.adapter.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 框架端点豁免谓词的守护测试（ADR-013 增值点 4）。 */
class FrameworkEndpointsTest {

    @Test
    void 框架端点前缀命中() {
        assertThat(FrameworkEndpoints.matches("/v3/api-docs")).isTrue();
        assertThat(FrameworkEndpoints.matches("/v3/api-docs/swagger-config")).isTrue();
        assertThat(FrameworkEndpoints.matches("/doc.html")).isTrue();
        assertThat(FrameworkEndpoints.matches("/webjars/knife4j/abc.js")).isTrue();
        assertThat(FrameworkEndpoints.matches("/swagger-ui/index.html")).isTrue();
    }

    @Test
    void 业务路径不命中() {
        assertThat(FrameworkEndpoints.matches("/orders/42")).isFalse();
        assertThat(FrameworkEndpoints.matches("/v3/report")).isFalse();
        assertThat(FrameworkEndpoints.matches(null)).isFalse();
    }
}
