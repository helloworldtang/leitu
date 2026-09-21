package cn.youhuale.leitu.starter;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 文档接线的装配金测试：OpenAPI 信息 Bean 的缺省/覆盖/用户优先。 */
class LeituOpenApiWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LeituConfigAutoConfiguration.class,
                    LeituOpenApiAutoConfiguration.class));

    @Test
    void 默认OpenAPI信息就位() {
        runner.run(ctx -> {
            OpenAPI api = ctx.getBean(OpenAPI.class);
            assertThat(api.getInfo().getTitle()).isEqualTo("服务 API");
            assertThat(api.getInfo().getVersion()).isEqualTo("0.0.0");
        });
    }

    @Test
    void 配置覆盖标题与版本() {
        runner.withPropertyValues("leitu.openapi.title=订单服务", "leitu.openapi.version=1.2.3").run(ctx -> {
            OpenAPI api = ctx.getBean(OpenAPI.class);
            assertThat(api.getInfo().getTitle()).isEqualTo("订单服务");
            assertThat(api.getInfo().getVersion()).isEqualTo("1.2.3");
        });
    }

    @Test
    void 用户OpenAPI优先() {
        OpenAPI custom = new OpenAPI();
        runner.withBean(OpenAPI.class, () -> custom).run(ctx ->
                assertThat(ctx.getBean(OpenAPI.class)).isSameAs(custom));
    }
}
