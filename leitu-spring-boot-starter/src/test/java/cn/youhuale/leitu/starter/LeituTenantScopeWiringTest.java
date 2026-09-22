package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.adapter.jdbc.JdbcAdapterAutoConfiguration;
import cn.youhuale.leitu.adapter.jdbc.JdbcAudit;
import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.adapter.jdbc.JdbcMapping;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配金测试：租户作用域的来源必须是<b>容器里那个</b>读取器，不是全局线程绑定的静态单例。
 *
 * <p>这里刻意用一个「非线程」的自定义 {@code ExecutionContextBinder}（普通可变字段）——
 * 只要装配层有任何一处回落到 {@code ExecutionContextReader.threadLocal()}，存取器就读不到
 * 这个 binder 的上下文，行会落进匿名租户 "-"，本测试立刻变红。
 *
 * <p>反向自测性质：这是「默认路径恰好等价、扩展路径静默出错」这一类缺陷的唯一守门人。
 */
class LeituTenantScopeWiringTest {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS starter_orders (
              tenant       VARCHAR(64)  NOT NULL,
              id           VARCHAR(64)  NOT NULL,
              created_by   VARCHAR(128) NOT NULL,
              created_at   TIMESTAMP    NOT NULL,
              updated_by   VARCHAR(128) NOT NULL,
              updated_at   TIMESTAMP    NOT NULL,
              amount_cents BIGINT       NOT NULL,
              PRIMARY KEY (tenant, id)
            )""";

    record StarterOrder(String id, AuditFields auditFields, long amountCents) implements Auditable {
        static StarterOrder create(String id, long amountCents) {
            return new StarterOrder(id, AuditFields.empty(), amountCents);
        }

        @Override
        public StarterOrder withAuditFields(AuditFields auditFields) {
            return new StarterOrder(id, auditFields, amountCents);
        }
    }

    private static final JdbcMapping<StarterOrder, String> MAPPING = JdbcMapping.<StarterOrder, String>builder()
            .table("starter_orders")
            .columns("amount_cents")
            .idOf(StarterOrder::id)
            .values(o -> List.of(o.amountCents()))
            .rowMapper((rs, rowNum) -> new StarterOrder(
                    rs.getString("id"), JdbcAudit.fields(rs), rs.getLong("amount_cents")))
            .build();

    /** 刻意不用 ThreadLocal——正是为了暴露任何「回落到线程级绑定」的写法。 */
    static final class HolderBinder implements ExecutionContextBinder {
        private volatile ExecutionContext current = ExecutionContext.anonymous();

        @Override
        public ExecutionContext current() {
            return current;
        }

        @Override
        public Scope bind(ExecutionContext context) {
            ExecutionContext previous = current;
            current = Objects.requireNonNull(context, "context 必填");
            return () -> current = previous;
        }
    }

    private static DataSource dataSource;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:leitu_starter_ctx;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        JdbcClient.create(ds).sql(DDL).update();
        dataSource = ds;
    }

    @BeforeEach
    void clean() {
        JdbcClient.create(dataSource).sql("DELETE FROM starter_orders").update();
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LeituContextAutoConfiguration.class,
                    JdbcAdapterAutoConfiguration.class))
            .withBean(DataSource.class, () -> dataSource);

    @Test
    void 自定义绑定器_存取器与之同源_行落在绑定器给的租户() {
        HolderBinder binder = new HolderBinder();
        runner.withBean(ExecutionContextBinder.class, () -> binder).run(ctx -> {
            DataStore<StarterOrder, String> store =
                    ctx.getBean(JdbcDataStoreFactory.class).create(MAPPING);

            try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-custom"), "t-1"))) {
                store.save(StarterOrder.create("o-1", 100L));
            }

            // 绕过存取器直接查库：行究竟落在哪个租户，这里说了算
            String tenant = JdbcClient.create(dataSource)
                    .sql("SELECT tenant FROM starter_orders WHERE id = 'o-1'")
                    .query(String.class).single();
            assertThat(tenant).as("行必须落在自定义绑定器的租户，而不是匿名兜底 \"-\"")
                    .isEqualTo("tenant-custom");
        });
    }

    @Test
    void 未绑定时_落匿名租户_但不静默串到他租户() {
        HolderBinder binder = new HolderBinder();
        runner.withBean(ExecutionContextBinder.class, () -> binder).run(ctx -> {
            DataStore<StarterOrder, String> store =
                    ctx.getBean(JdbcDataStoreFactory.class).create(MAPPING);

            store.save(StarterOrder.create("o-anon", 1L));

            String tenant = JdbcClient.create(dataSource)
                    .sql("SELECT tenant FROM starter_orders WHERE id = 'o-anon'")
                    .query(String.class).single();
            assertThat(tenant).isEqualTo("-");

            // 匿名行不出现在任何具名租户的视野里
            try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-custom"), "t-2"))) {
                assertThat(store.findById("o-anon")).isEmpty();
            }
        });
    }
}
