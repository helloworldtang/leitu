package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.config.spi.ConfigSource;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Environment → 标准配置源的桥（starter 内部件）：读时求值；诊断面只带身份不带值。
 *
 * <p><b>为什么要拆成两层</b>：Spring 的 Environment 是"一条链里装着两种东西"——
 * 命令行参数/系统属性/系统环境变量是<b>运行时覆盖</b>（启动这一次的意志），
 * {@code application.yml} 等文件是<b>本地默认值</b>（打进制品的基线）。
 * 把整条 Environment 当一层放在链头，本地 yml 就压过了配置中心：
 * 制品里写死的值赢过运维在配置中心改的值——与"配置中心是运行期事实源"的惯例相反，
 * 且这类错误只在"运维改了配置却不生效"时才被发现。
 *
 * <p>于是拆成两个源分别入链：{@link #overrides} 置链头（配置中心之上），
 * {@link #files} 置在应用声明源之后（配置中心之下）。
 */
final class EnvironmentConfigSource implements ConfigSource {

    /** 运行时覆盖层的三个 PropertySource 名（Spring 标准命名）。 */
    private static final Set<String> OVERRIDE_SOURCE_NAMES = Set.of(
            CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

    private final String name;

    private final List<PropertySource<?>> sources;

    private EnvironmentConfigSource(ConfigurableEnvironment environment, String name, boolean overrides) {
        this.name = name;
        List<PropertySource<?>> picked = new ArrayList<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (OVERRIDE_SOURCE_NAMES.contains(source.getName()) == overrides) {
                picked.add(source);
            }
        }
        this.sources = List.copyOf(picked);
    }

    /** 运行时覆盖层：命令行参数 → 系统属性 → 系统环境变量（按 Spring 自身顺序，first-match-wins）。 */
    static ConfigSource overrides(Environment environment) {
        return new EnvironmentConfigSource(configurable(environment), "spring-overrides", true);
    }

    /** 本地文件层：application.yml / application-{profile}.yml 等制品内基线。 */
    static ConfigSource files(Environment environment) {
        return new EnvironmentConfigSource(configurable(environment), "spring-files", false);
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * 沿本层的 PropertySource 现查，首个有值者赢。
     *
     * <p>逐源现查而非 {@code environment.getProperty(key)}：后者跨全链求值，
     * 无法把"运行时覆盖"与"本地文件"分开入链——那正是本次要修的倒置。
     */
    @Override
    public Optional<String> get(String key) {
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) {
                return Optional.of(String.valueOf(value));
            }
        }
        return Optional.empty();
    }

    private static ConfigurableEnvironment configurable(Environment environment) {
        Objects.requireNonNull(environment, "environment 必填");
        if (!(environment instanceof ConfigurableEnvironment configurable)) {
            throw new IllegalArgumentException(
                    "Environment 必须是 ConfigurableEnvironment（要枚举 PropertySource 才能把覆盖层与文件层分开入链）：当前 "
                            + environment.getClass().getName());
        }
        return configurable;
    }

    @Override
    public String toString() {
        return "EnvironmentConfigSource[" + name + "，" + sources.size() + " 个 PropertySource]";
    }
}
