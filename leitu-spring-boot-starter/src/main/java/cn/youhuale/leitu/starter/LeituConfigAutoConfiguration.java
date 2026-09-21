package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.config.spi.ConfigSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置（config-source）的默认装配：把 Spring {@link Environment} 桥接为一个标准配置源（插链头），
 * 应用声明的 {@link ConfigSource} Bean 其次，core 三源兜底其后——单一配置链路：
 * 读时求值、优先级=组合顺序（一处可见）、值不上诊断面。
 */
@AutoConfiguration
public class LeituConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigReader configReader(Environment environment, ObjectProvider<ConfigSource> sources) {
        List<ConfigSource> chain = new ArrayList<>();
        chain.add(new EnvironmentConfigSource(environment));
        sources.orderedStream().forEach(chain::add);
        chain.add(ConfigSources.systemProperties());
        chain.add(ConfigSources.environmentVariables());
        chain.add(ConfigSources.classpathProperties("leitu.properties"));
        return ConfigReader.of(chain.toArray(ConfigSource[]::new));
    }
}
