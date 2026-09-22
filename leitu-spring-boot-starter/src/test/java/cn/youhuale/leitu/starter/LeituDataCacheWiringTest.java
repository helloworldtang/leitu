package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.adapter.jdbc.JdbcAdapterAutoConfiguration;
import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据与缓存的装配金测试：CachePolicy 缺省/配置覆盖；
 * JdbcDataStoreFactory 的条件装配——有 DataSource 才有，用户 Bean 优先。
 */
class LeituDataCacheWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LeituContextAutoConfiguration.class,
                    LeituGuardAutoConfiguration.class,
                    LeituObserveAutoConfiguration.class,
                    LeituConfigAutoConfiguration.class,
                    LeituCacheAutoConfiguration.class,
                    JdbcAdapterAutoConfiguration.class));

    @Test
    void CachePolicy_缺省值() {
        runner.run(ctx -> {
            CachePolicy policy = ctx.getBean(CachePolicy.class);
            assertThat(policy.maxSize()).isEqualTo(1000);
            assertThat(policy.ttl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(policy.enabled()).isTrue();
        });
    }

    @Test
    void CachePolicy_配置覆盖() {
        runner.withPropertyValues("cache.max-size=50", "cache.enabled=false").run(ctx -> {
            CachePolicy policy = ctx.getBean(CachePolicy.class);
            assertThat(policy.maxSize()).isEqualTo(50);
            assertThat(policy.enabled()).isFalse();
        });
    }

    @Test
    void 无DataSource_不装配Factory_有则装配() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(JdbcDataStoreFactory.class));
        DataSource ds = Mockito.mock(DataSource.class);
        runner.withBean(DataSource.class, () -> ds).run(ctx ->
                assertThat(ctx).hasSingleBean(JdbcDataStoreFactory.class));
    }

    @Test
    void 用户Factory优先() {
        DataSource ds = Mockito.mock(DataSource.class);
        JdbcDataStoreFactory custom = new JdbcDataStoreFactory(
                ds, ExecutionContextReader.threadLocal(), Clock.systemUTC());
        runner.withBean(DataSource.class, () -> ds)
                .withBean(JdbcDataStoreFactory.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(JdbcDataStoreFactory.class)).isSameAs(custom));
    }
}
