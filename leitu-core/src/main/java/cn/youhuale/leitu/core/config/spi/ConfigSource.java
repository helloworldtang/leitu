package cn.youhuale.leitu.core.config.spi;

import java.util.Optional;

/**
 * 配置源——配置值的一个来源，"配置值从哪来"的答案侧。
 *
 * <p>adapter/宿主实现（配置中心 Nacos/Apollo、启动参数、测试假源……）；core 内置三源兜底
 * 见 {@code ConfigSources}（系统属性 / 环境变量 / classpath properties）。
 *
 * <p>约定：
 * <ul>
 *   <li>源不声明优先级——优先级=组合顺序（{@code ConfigReader.of(源…)} 参数顺序，一处可见）</li>
 *   <li>无动态标记、无变更监听——读时求值；动态性是配置中心 adapter 的事</li>
 *   <li>name() 是诊断身份（进读取器 toString 与教学异常），永不携带配置值</li>
 * </ul>
 */
public interface ConfigSource {

    /** 诊断身份：如 "系统属性"、"classpath:leitu.properties"。 */
    String name();

    /** 按 key 取值；缺失返回 Optional.empty() 是合法态（配置可以没有这一项）。 */
    Optional<String> get(String key);
}
