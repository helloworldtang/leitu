package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * JDBC 适配装配：容器里有 DataSource 时提供 {@link JdbcDataStoreFactory}（用户 Bean 优先）。
 * 排在宿主 DataSource 装配之后（afterName 指向 Boot 4 的 spring-boot-jdbc 模块）。
 *
 * <p>读取器取<b>容器里的</b> {@code ExecutionContextReader} Bean——不是全局线程绑定的静态单例：
 * 应用一旦声明自己的 {@code ExecutionContextBinder}（异步 / MDC / 网关透传），入口与存取器必须
 * 读到同一个上下文，否则租户隔离形同虚设（行被静默写进匿名租户 "-"，不报错、不告警，
 * 只在跨租户串味时才被发现）。缺该 Bean 时容器启动即失败（大声），胜过运行期静默错租户。
 * 时钟无 Bean 时回落系统 UTC——时刻不决定作用域，属可给默认值的维度。
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class JdbcAdapterAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public JdbcDataStoreFactory jdbcDataStoreFactory(DataSource dataSource,
                                                     ExecutionContextReader reader,
                                                     ObjectProvider<Clock> clock) {
        return new JdbcDataStoreFactory(dataSource, reader, clock.getIfAvailable(Clock::systemUTC));
    }
}
