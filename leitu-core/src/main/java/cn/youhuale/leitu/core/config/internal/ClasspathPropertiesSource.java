package cn.youhuale.leitu.core.config.internal;

import cn.youhuale.leitu.core.config.spi.ConfigSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** classpath properties 源：构造时装载一次（运行期不可变）。外部经 {@code ConfigSources.classpathProperties(...)} 获取，不直接实例化。 */
public final class ClasspathPropertiesSource implements ConfigSource {

    private final String resourceName;

    private final Map<String, String> values;

    public ClasspathPropertiesSource(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException(
                    "resourceName 必填：classpath 资源名（如 \"leitu.properties\"）");
        }
        this.resourceName = resourceName;
        this.values = load(resourceName);
    }

    private static Map<String, String> load(String resourceName) {
        ClassLoader loader = firstNonNull(
                Thread.currentThread().getContextClassLoader(),
                ClasspathPropertiesSource.class.getClassLoader(),
                ClassLoader.getSystemClassLoader());
        var url = loader.getResource(resourceName);
        if (url == null) {
            return Map.of();    // 文件不存在是常态（不是每个应用都有 leitu.properties），空源即可
        }
        Properties props = new Properties();
        try (InputStream in = url.openStream()) {
            props.load(in);
        } catch (IOException e) {
            // 接线期大声失败：指名加载却读不进，吞掉=启动后每个值悄悄走默认
            throw new IllegalArgumentException(
                    "classpath 资源 " + resourceName + " 读取失败（接线期大声失败，不带出内容）：" + e,
                    e);
        }
        Map<String, String> copied = new HashMap<>();
        for (String name : props.stringPropertyNames()) {
            copied.put(name, props.getProperty(name));
        }
        return Map.copyOf(copied);
    }

    private static ClassLoader firstNonNull(ClassLoader... loaders) {
        for (ClassLoader l : loaders) {
            if (l != null) {
                return l;
            }
        }
        throw new IllegalStateException("找不到可用的 ClassLoader");
    }

    @Override
    public String name() {
        return "classpath:" + resourceName;
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
