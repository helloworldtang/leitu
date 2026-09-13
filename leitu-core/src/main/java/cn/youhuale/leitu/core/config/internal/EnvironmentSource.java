package cn.youhuale.leitu.core.config.internal;

import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** 环境变量源。外部经 {@code ConfigSources.environmentVariables()} 获取，不直接实例化。 */
public final class EnvironmentSource implements ConfigSource {

    private final Function<String, String> lookup;

    /** 默认读真实环境变量。 */
    public EnvironmentSource() {
        this(System::getenv);
    }

    /** 注入读取函数（测试缝）。 */
    public EnvironmentSource(Function<String, String> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup 必填：测试注入环境读取函数");
    }

    @Override
    public String name() {
        return "环境变量";
    }

    @Override
    public Optional<String> get(String key) {
        String exact = lookup.apply(key);
        if (exact != null) {
            return Optional.of(exact);
        }
        return Optional.ofNullable(lookup.apply(relaxed(key)));
    }

    /** 点分/划线键 → 环境变量形（datasource.url → DATASOURCE_URL）。OS 不允许环境变量含点，不归一则点分键在本源形同虚设。 */
    public static String relaxed(String key) {
        return key.replace('-', '_').replace('.', '_').toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return name();
    }
}
