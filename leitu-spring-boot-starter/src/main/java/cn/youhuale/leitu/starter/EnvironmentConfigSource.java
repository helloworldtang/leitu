package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.config.spi.ConfigSource;
import org.springframework.core.env.Environment;

import java.util.Objects;
import java.util.Optional;

/**
 * Spring Environment → 标准配置源的桥（starter 内部件）：读时求值；
 * 诊断面只带身份不带值（值静默教义，config-source 答案）。
 */
final class EnvironmentConfigSource implements ConfigSource {

    private final Environment environment;

    EnvironmentConfigSource(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "environment 必填");
    }

    @Override
    public String name() {
        return "spring-environment";
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(environment.getProperty(key));
    }

    @Override
    public String toString() {
        return "EnvironmentConfigSource[spring-environment]";
    }
}
