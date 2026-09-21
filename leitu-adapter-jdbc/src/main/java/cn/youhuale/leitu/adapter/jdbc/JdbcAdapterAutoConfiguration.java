package cn.youhuale.leitu.adapter.jdbc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * JDBC 适配装配：容器里有 DataSource 时提供 {@link JdbcDataStoreFactory}（用户 Bean 优先）。
 * 排在宿主 DataSource 装配之后（afterName 指向 Boot 4 的 spring-boot-jdbc 模块）。
 */
@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class JdbcAdapterAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public JdbcDataStoreFactory jdbcDataStoreFactory(DataSource dataSource) {
        return new JdbcDataStoreFactory(dataSource);
    }
}
