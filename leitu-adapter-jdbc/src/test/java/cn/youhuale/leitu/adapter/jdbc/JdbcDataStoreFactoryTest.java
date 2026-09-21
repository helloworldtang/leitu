package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.api.DataStores;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金测试：JdbcDataStore 落实三条合同（租户作用域 / 审计盖章 / 租户内主键唯一）。
 * H2 内存库；core / capability / adapter 的任何改动让这里变红，即破坏了既有答案。
 */
class JdbcDataStoreFactoryTest {

    private static final String TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS test_orders (
              tenant       VARCHAR(64)  NOT NULL,
              id           VARCHAR(64)  NOT NULL,
              created_by   VARCHAR(128) NOT NULL,
              created_at   TIMESTAMP    NOT NULL,
              updated_by   VARCHAR(128) NOT NULL,
              updated_at   TIMESTAMP    NOT NULL,
              amount_cents BIGINT       NOT NULL,
              PRIMARY KEY (tenant, id)
            )""";

    record TestOrder(String id, AuditFields auditFields, long amountCents) implements Auditable {
        static TestOrder create(String id, long amountCents) {
            return new TestOrder(id, AuditFields.empty(), amountCents);
        }

        @Override
        public TestOrder withAuditFields(AuditFields auditFields) {
            return new TestOrder(id, auditFields, amountCents);
        }
    }

    private static final JdbcMapping<TestOrder, String> MAPPING = JdbcMapping.<TestOrder, String>builder()
            .table("test_orders")
            .columns("amount_cents")
            .idOf(TestOrder::id)
            .values(o -> List.of(o.amountCents()))
            .rowMapper((rs, rowNum) -> new TestOrder(
                    rs.getString("id"), JdbcAudit.fields(rs), rs.getLong("amount_cents")))
            .build();

    private static DataSource dataSource;
    private static JdbcDataStoreFactory factory;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:leitu_jdbc_test;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        dataSource = ds;
        JdbcClient.create(ds).sql(TABLE_DDL).update();
        factory = new JdbcDataStoreFactory(ds);
    }

    @BeforeEach
    void clean() {
        JdbcClient.create(dataSource).sql("DELETE FROM test_orders").update();
    }

    private static <X> X as(String subject, String tenant, Supplier<X> op) {
        try (var scope = ExecutionContextBinders.threadLocal().bind(
                ExecutionContext.of(Operator.human(subject, tenant), "t-" + subject + "-" + tenant))) {
            return op.get();
        }
    }

    private DataStore<TestOrder, String> store() {
        return factory.create(MAPPING);
    }

    @Test
    void 插入_自动盖章_四件同源同刻() {
        TestOrder saved = as("alice", "tenant-a", () -> store().save(TestOrder.create("o-1", 9900L)));
        assertThat(saved.auditFields().stamped()).isTrue();
        assertThat(saved.auditFields().createdBy()).isEqualTo("alice");
        assertThat(saved.auditFields().updatedBy()).isEqualTo("alice");
        assertThat(saved.auditFields().createdAt()).isEqualTo(saved.auditFields().updatedAt());
    }

    @Test
    void 读回_行变实体_审计随行() {
        as("alice", "tenant-a", () -> store().save(TestOrder.create("o-1", 9900L)));
        TestOrder found = as("alice", "tenant-a", () -> store().findById("o-1")).orElseThrow();
        assertThat(found.amountCents()).isEqualTo(9900L);
        assertThat(found.auditFields().createdBy()).isEqualTo("alice");
    }

    @Test
    void 更新_保留created_换新updated() {
        as("alice", "tenant-a", () -> store().save(TestOrder.create("o-1", 9900L)));
        TestOrder updated = as("bob", "tenant-a", () -> store().save(TestOrder.create("o-1", 12800L)));
        assertThat(updated.amountCents()).isEqualTo(12800L);
        assertThat(updated.auditFields().createdBy()).isEqualTo("alice");
        assertThat(updated.auditFields().updatedBy()).isEqualTo("bob");
    }

    @Test
    void 租户隔离_不可见_不泄漏_不报错() {
        as("alice", "tenant-a", () -> store().save(TestOrder.create("o-1", 9900L)));
        assertThat(as("mallory", "tenant-b", () -> store().findById("o-1"))).isEmpty();
        assertThat(as("mallory", "tenant-b", () -> store().findAll())).isEmpty();
        assertThat(as("mallory", "tenant-b", () -> store().deleteById("o-1"))).isFalse();
        assertThat(as("alice", "tenant-a", () -> store().findById("o-1"))).isPresent();
    }

    @Test
    void 跨租户同主键_两行() {
        as("alice", "tenant-a", () -> store().save(TestOrder.create("o-1", 100L)));
        as("bob", "tenant-b", () -> store().save(TestOrder.create("o-1", 200L)));
        assertThat(as("alice", "tenant-a", () -> store().findById("o-1")).orElseThrow().amountCents()).isEqualTo(100L);
        assertThat(as("bob", "tenant-b", () -> store().findById("o-1")).orElseThrow().amountCents()).isEqualTo(200L);
    }

    @Test
    void 系统作用域_减号只对减号可见() {
        as("system", "-", () -> store().save(TestOrder.create("sys-1", 1L)));
        assertThat(as("alice", "tenant-a", () -> store().findById("sys-1"))).isEmpty();
        assertThat(as("sysop", "-", () -> store().findById("sys-1"))).isPresent();
    }

    @Test
    void deleteById_命中与未命中() {
        as("alice", "tenant-a", () -> store().save(TestOrder.create("o-1", 100L)));
        assertThat(as("alice", "tenant-a", () -> store().deleteById("o-1"))).isTrue();
        assertThat(as("alice", "tenant-a", () -> store().deleteById("o-1"))).isFalse();
    }

    @Test
    void findAll_当前租户全量_不含他租户() {
        as("alice", "tenant-a", () -> {
            store().save(TestOrder.create("o-1", 1L));
            store().save(TestOrder.create("o-2", 2L));
            return null;
        });
        as("bob", "tenant-b", () -> {
            store().save(TestOrder.create("o-3", 3L));
            return null;
        });
        assertThat(as("alice", "tenant-a", () -> store().findAll())).hasSize(2);
    }

    @Test
    void 观察装饰可组合_opt_in() {
        List<ObservationEvent> events = new ArrayList<>();
        DataStore<TestOrder, String> observed = DataStores.observing(store(), TestOrder::id, events::add);
        as("alice", "tenant-a", () -> observed.save(TestOrder.create("o-1", 100L)));
        assertThat(events).anyMatch(e -> e.name().equals("data.saved"));
    }
}
