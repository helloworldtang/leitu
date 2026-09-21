package cn.youhuale.leitu.adapter.web;

import cn.youhuale.leitu.adapter.web.internal.FailureAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * web 适配装配：Servlet web 应用自动注册失败投影（problem+json）。
 * 开关：{@code leitu.web.advice-enabled}（缺省 true）；用户 Bean 优先。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class WebAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "leitu.web", name = "advice-enabled", havingValue = "true", matchIfMissing = true)
    public FailureAdvice failureAdvice() {
        return new FailureAdvice();
    }
}
