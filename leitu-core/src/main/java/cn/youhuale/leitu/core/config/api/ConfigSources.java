package cn.youhuale.leitu.core.config.api;

import cn.youhuale.leitu.core.config.internal.ClasspathPropertiesSource;
import cn.youhuale.leitu.core.config.internal.EnvironmentSource;
import cn.youhuale.leitu.core.config.internal.MapConfigSource;
import cn.youhuale.leitu.core.config.internal.SystemPropertySource;
import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.util.Map;
import java.util.function.Function;

/**
 * 配置源的工厂——宿主/测试从 api 取内置实现，不触碰 internal。
 *
 * <p>配置中心（Nacos/Apollo）= adapter 自行实现 {@link ConfigSource} 后经
 * {@code ConfigReader.of(...)} 插入；{@link #fromMap} 是测试与演示的最小源。
 */
public final class ConfigSources {

    private ConfigSources() {
    }

    /** 系统属性源（System.getProperty）。 */
    public static ConfigSource systemProperties() {
        return new SystemPropertySource();
    }

    /** 环境变量源（System.getenv）；对点分键同时查归一形（如 datasource.url → DATASOURCE_URL）。 */
    public static ConfigSource environmentVariables() {
        return new EnvironmentSource();
    }

    /** 环境变量源，注入读取函数（测试缝：金测试要能锁定归一行为，真实环境变量在测试里不可控）。 */
    public static ConfigSource environmentVariables(Function<String, String> lookup) {
        return new EnvironmentSource(lookup);
    }

    /** classpath properties 源：构造时装载一次（运行期不可变）；文件缺失=空源是常态。 */
    public static ConfigSource classpathProperties(String resourceName) {
        return new ClasspathPropertiesSource(resourceName);
    }

    /** Map 源（测试与演示）：name 是诊断身份，values 防御拷贝。 */
    public static ConfigSource fromMap(String name, Map<String, String> values) {
        return new MapConfigSource(name, values);
    }
}
