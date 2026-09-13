package cn.youhuale.leitu.core.config.api;

import cn.youhuale.leitu.core.config.internal.DefaultConfigReader;
import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.time.Duration;
import java.util.Optional;

/**
 * 端口：读配置——业务代码认识"配置值从哪来"的全部接口。
 *
 * <p>一次读取沿源链现查（first-match-wins，读时求值无订阅）；缺失→默认值是正常态，
 * 有值但解析失败→错误即教程（异常带 key、源名、期望格式与示例，永不带值）。
 *
 * <p>用法：
 * <pre>{@code
 * ConfigReader config = ConfigReader.standard();   // 三源兜底
 * int pool = config.getInt("datasource.pool", 10);
 * Duration timeout = config.getDuration("http.timeout", Duration.ofSeconds(5));
 * }</pre>
 */
public interface ConfigReader {

    /** 缺失返回 Optional.empty()——「没有默认值」的诚实通道。key 非空非空白。 */
    Optional<String> get(String key);

    /** 缺失→fallback；fallback 禁 null（没有默认值就改用 {@link #get(String)}）。 */
    String get(String key, String fallback);

    /** 十进制整数（示例 42）；缺失→fallback，有值但非法→教学异常。 */
    int getInt(String key, int fallback);

    /** 十进制长整数（示例 9000000000）；缺失→fallback，有值但非法→教学异常。 */
    long getLong(String key, long fallback);

    /** 仅 true/false（忽略大小写）；缺失→fallback，有值但非法→教学异常（绝不静默当 false）。 */
    boolean getBoolean(String key, boolean fallback);

    /** ISO-8601 时长（示例 PT30S，JDK Duration.parse）；缺失→fallback，有值但非法→教学异常。 */
    Duration getDuration(String key, Duration fallback);

    /** 枚举名（大小写敏感）；缺失→fallback，有值但非法→教学异常（列出全部合法名）。 */
    <E extends Enum<E>> E getEnum(String key, Class<E> type, E fallback);

    /**
     * 组合读取器：参数顺序即优先级（前源先赢，first-match-wins）。空数组合法——
     * 一切读取走默认值；null 元素视为装配错误，教学式失败。
     */
    static ConfigReader of(ConfigSource... sources) {
        return new DefaultConfigReader(java.util.Arrays.asList(sources));
    }

    /** 三源兜底：系统属性 → 环境变量 → classpath leitu.properties（文件缺失=空源是常态）。 */
    static ConfigReader standard() {
        return of(ConfigSources.systemProperties(),
                ConfigSources.environmentVariables(),
                ConfigSources.classpathProperties("leitu.properties"));
    }
}
