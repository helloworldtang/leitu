package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * API 文档（api-contract）的接线：springdoc + knife4j（ADR-013）。
 * 提供基础 OpenAPI 信息 Bean（配置可覆盖：leitu.openapi.title / version / description）。
 *
 * <p>四条增值点的落位：① FailureNotice→problem+json 投影在 leitu-adapter-web；
 * ② traceId 响应头由投影携带（X-Trace-Id）；③ 判定链 action 与 operationId 点分同源
 * （建议 + 金样本示范）；④ 框架端点豁免谓词 FrameworkEndpoints（leitu-adapter-web）。
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class LeituOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI leituOpenApi(ConfigReader config) {
        return new OpenAPI().info(new Info()
                .title(config.get("leitu.openapi.title", "服务 API"))
                .version(config.get("leitu.openapi.version", "0.0.0"))
                .description(config.get("leitu.openapi.description",
                        "由累土接线：失败交代为 problem+json（RFC 9457），traceId 随响应头 X-Trace-Id")));
    }
}
