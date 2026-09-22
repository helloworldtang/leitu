package cn.youhuale.leitu.adapter.web;

import cn.youhuale.leitu.adapter.web.internal.FailureAdvice;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * web 适配装配：Servlet web 应用自动注册失败投影（problem+json）。
 * 开关：{@code leitu.web.advice-enabled}（缺省 true）；用户 Bean 优先。
 *
 * <p>读取器取<b>容器里的</b> {@code ExecutionContextReader} Bean（非全局线程绑定的静态单例）——
 * 否则应用自定义 {@code ExecutionContextBinder} 时，投影带的是匿名 traceId，
 * 调用方拿到的 traceId 串不起来。记录器无 Bean 时回落日志级默认（观测维度的合法默认值）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class WebAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "leitu.web", name = "advice-enabled", havingValue = "true", matchIfMissing = true)
    public FailureAdvice failureAdvice(ExecutionContextReader reader,
                                       ObjectProvider<ObservationRecorder> recorder) {
        return new FailureAdvice(reader, recorder.getIfAvailable(ObservationRecorder::of));
    }
}
