package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 执行上下文（who-is-operating）的默认装配：线程级绑定 + 只读读取器。
 *
 * <p>绑定是入口/宿主的事——应用在请求入口用注入的 {@link ExecutionContextBinder}
 * try-with-resources 绑定；业务代码注入 {@link ExecutionContextReader} 只读。
 */
@AutoConfiguration
public class LeituContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextBinder executionContextBinder() {
        return ExecutionContextBinders.threadLocal();
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextReader executionContextReader(ExecutionContextBinder binder) {
        return ExecutionContextReader.of(binder);
    }
}
