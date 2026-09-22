package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.config.spi.ConfigSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置（config-source）的默认装配——单一配置链路：读时求值、优先级=组合顺序（一处可见）、值不上诊断面。
 *
 * <p>链（前赢后）：
 * <ol>
 *   <li>{@code spring-overrides}：命令行参数 / 系统属性 / 系统环境变量——<b>启动这一次的意志</b>，最高；</li>
 *   <li>应用声明的 {@link ConfigSource} Bean（配置中心 Nacos/Apollo 等）——<b>运行期事实源</b>；</li>
 *   <li>{@code spring-files}：{@code application.yml} 等制品内基线——<b>默认值</b>，配置中心之下；</li>
 *   <li>{@code classpath:leitu.properties}：最后兜底。</li>
 * </ol>
 *
 * <p>为什么把 Environment 拆成两层而不是整条置链头：整条置链头时本地 yml 会压过配置中心——
 * 制品里写死的值赢过运维在配置中心改的值，与业界惯例相反，且只在"运维改了配置却不生效"时才暴露
 * （详见 {@link EnvironmentConfigSource}）。系统属性与环境变量已由第 1 层覆盖，
 * 故不再在链尾重复（避免死链——看着有源，实际永不被问到）。
 */
@AutoConfiguration
public class LeituConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigReader configReader(Environment environment, ObjectProvider<ConfigSource> sources) {
        List<ConfigSource> chain = new ArrayList<>();
        chain.add(EnvironmentConfigSource.overrides(environment));
        sources.orderedStream().forEach(chain::add);
        chain.add(EnvironmentConfigSource.files(environment));
        chain.add(ConfigSources.classpathProperties("leitu.properties"));
        return ConfigReader.of(chain.toArray(ConfigSource[]::new));
    }
}
