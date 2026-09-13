package cn.youhuale.leitu.core.config.internal;

import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.util.Optional;

/** 系统属性源。外部经 {@code ConfigSources.systemProperties()} 获取，不直接实例化。 */
public final class SystemPropertySource implements ConfigSource {

    @Override
    public String name() {
        return "系统属性";
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(System.getProperty(key));
    }

    @Override
    public String toString() {
        return name();
    }
}
