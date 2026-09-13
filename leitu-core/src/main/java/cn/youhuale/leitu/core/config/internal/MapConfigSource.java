package cn.youhuale.leitu.core.config.internal;

import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Map 源（测试与演示的最小源）。外部经 {@code ConfigSources.fromMap(...)} 获取，不直接实例化。 */
public final class MapConfigSource implements ConfigSource {

    private final String name;

    private final Map<String, String> values;

    public MapConfigSource(String name, Map<String, String> values) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 必填：源名是诊断身份（进 toString 与教学异常）");
        }
        Objects.requireNonNull(values, "values 必填：空源传 Map.of()");
        this.name = name;
        this.values = Map.copyOf(values);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public String toString() {
        return name();
    }
}
