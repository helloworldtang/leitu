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

    /**
     * 反向自测：应用部署在 context-path 下时，requestURI 是含前缀的全路径。
     * 忘了传 contextPath，豁免就永不命中——文档端点被响应包装包一层，
     * 现象却是"swagger 打不开"，排查方向整个跑偏。
     */
    @Test
    void contextPath下_剥离后才命中() {
        assertThat(FrameworkEndpoints.matches("/app/v3/api-docs", "/app")).isTrue();
        assertThat(FrameworkEndpoints.matches("/app/doc.html", "/app")).isTrue();
        assertThat(FrameworkEndpoints.matches("/app/orders/42", "/app")).isFalse();
        // 单参版拿不到 contextPath：这条正是它失效的形态（保留行为，靠 javadoc 明示）
        assertThat(FrameworkEndpoints.matches("/app/v3/api-docs")).isFalse();
        // contextPath 为 "/" 或空时不做剥离（根部署）
        assertThat(FrameworkEndpoints.matches("/v3/api-docs", "/")).isTrue();
        assertThat(FrameworkEndpoints.matches("/v3/api-docs", "")).isTrue();
    }

    /**
     * 反向自测：前缀之后必须是路径边界。只做 startsWith 的话，
     * 一个名叫 /doc.htmlfoo 的业务端点会被当成文档端点豁免掉——
     * 它就此逃出统一响应包装，且只在有人起了个倒霉名字时才暴露。
     */
    @Test
    void 前缀必须落在路径边界上() {
        assertThat(FrameworkEndpoints.matches("/doc.htmlfoo")).isFalse();
        assertThat(FrameworkEndpoints.matches("/swagger-uix")).isFalse();
        assertThat(FrameworkEndpoints.matches("/v3/api-docsX")).isFalse();
        // 边界合法：斜杠、查询串、锚点、或就此结束
        assertThat(FrameworkEndpoints.matches("/doc.html?x=1")).isTrue();
        assertThat(FrameworkEndpoints.matches("/swagger-ui/index.html")).isTrue();
        assertThat(FrameworkEndpoints.matches("/swagger-ui.html")).isTrue();
    }
}
