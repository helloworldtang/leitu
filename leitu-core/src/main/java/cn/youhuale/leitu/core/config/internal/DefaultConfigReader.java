package cn.youhuale.leitu.core.config.internal;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/** 默认读取器实现。外部一律经 {@code ConfigReader.of(...)} 获取，不直接实例化本类。 */
public final class DefaultConfigReader implements ConfigReader {

    private final List<ConfigSource> sources;

    public DefaultConfigReader(List<ConfigSource> sources) {
        for (ConfigSource source : sources) {
            Objects.requireNonNull(source,
                    "source 必填：装配里混入了 null 源；省略即不装配（of() 空参合法——全部读取走默认值）");
        }
        this.sources = List.copyOf(sources);
    }

    @Override
    public Optional<String> get(String key) {
        requireKey(key);
        return firstHit(key).map(Hit::value);
    }

    @Override
    public String get(String key, String fallback) {
        requireKey(key);
        Objects.requireNonNull(fallback,
                "fallback 必填：缺失时无默认值就改用 get(key)——Optional 版才表达「没有默认」");
        return firstHit(key).map(Hit::value).orElse(fallback);
    }

    @Override
    public int getInt(String key, int fallback) {
        return mapPresent(key, hit -> {
            try {
                return Integer.parseInt(hit.value().strip());
            } catch (NumberFormatException e) {
                throw teach(key, hit.sourceName(), "int", "十进制整数", "42");
            }
        }, fallback);
    }

    @Override
    public long getLong(String key, long fallback) {
        return mapPresent(key, hit -> {
            try {
                return Long.parseLong(hit.value().strip());
            } catch (NumberFormatException e) {
                throw teach(key, hit.sourceName(), "long", "十进制长整数", "9000000000");
            }
        }, fallback);
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        return mapPresent(key, hit -> {
            String v = hit.value().strip();
            if (v.equalsIgnoreCase("true")) {
                return true;
            }
            if (v.equalsIgnoreCase("false")) {
                return false;
            }
            // 绝不静默当 false——那是把 typo 藏进默认值的事故配方
            throw teach(key, hit.sourceName(), "boolean", "true 或 false（忽略大小写）", "true");
        }, fallback);
    }

    @Override
    public Duration getDuration(String key, Duration fallback) {
        return mapPresent(key, hit -> {
            try {
                return Duration.parse(hit.value().strip());
            } catch (DateTimeParseException e) {
                throw teach(key, hit.sourceName(), "Duration", "ISO-8601 时长", "PT30S");
            }
        }, fallback);
    }

    @Override
    public <E extends Enum<E>> E getEnum(String key, Class<E> type, E fallback) {
        Objects.requireNonNull(type, "type 必填：枚举类型即合法值清单（错误消息要列出它）");
        E[] constants = type.getEnumConstants();
        return mapPresent(key, hit -> {
            String v = hit.value().strip();
            for (E c : constants) {
                if (c.name().equals(v)) {
                    return c;
                }
            }
            throw teach(key, hit.sourceName(), type.getSimpleName(),
                    "下列枚举名之一 " + Arrays.toString(constants), constants[0].name());
        }, fallback);
    }

    @Override
    public String toString() {
        if (sources.isEmpty()) {
            return "ConfigReader{空装配，全部读取走默认值}";
        }
        StringJoiner j = new StringJoiner(" → ", "ConfigReader{", "}");
        for (ConfigSource source : sources) {
            j.add(source.name());
        }
        return j.toString();
    }

    private record Hit(String value, String sourceName) {
    }

    /** 沿链现查，首个有值者赢；源抛异常大声传播（静默降级会把「配置中心挂了」伪装成「配置是旧值」）。 */
    private Optional<Hit> firstHit(String key) {
        for (ConfigSource source : sources) {
            Optional<String> value = source.get(key);
            if (value.isPresent()) {
                return Optional.of(new Hit(value.get(), source.name()));
            }
        }
        return Optional.empty();
    }

    private <T> T mapPresent(String key, java.util.function.Function<Hit, T> mapper, T fallback) {
        requireKey(key);
        Objects.requireNonNull(fallback, "fallback 必填：缺失时无默认值就改用 get(key)");
        return firstHit(key).map(mapper).orElse(fallback);
    }

    private static void requireKey(String key) {
        Objects.requireNonNull(key,
                "key 必填：配置按名读取；全量枚举/倾倒不是本端口的能力（值不上诊断面）");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key 不能为空白：配置键是检索键（如 \"datasource.url\"）");
        }
    }

    /** 统一教学异常：key + 源名 + 期望格式 + 示例——永不携带配置值（诊断面永不携带值，无豁免）。 */
    private static IllegalArgumentException teach(String key, String source,
                                                  String kind, String expected, String example) {
        return new IllegalArgumentException(
                "key=" + key + " 在源「" + source + "」里有值但不是合法" + kind
                        + "；期望：" + expected + "（示例 " + example + "）"
                        + "；值是什么请到该源查这个 key——诊断面永不携带配置值");
    }
}
