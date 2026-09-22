package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.api.DataStores;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** 审计列可空的"脏表"：专造 created_at 为 NULL 的行，验证报错指认得清（#9）。 */
    private static final String DIRTY_TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS test_orders_dirty (
              tenant       VARCHAR(64)  NOT NULL,
              id           VARCHAR(64)  NOT NULL,
              created_by   VARCHAR(128),
              created_at   TIMESTAMP,
              updated_by   VARCHAR(128),
              updated_at   TIMESTAMP,
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

    private static final JdbcMapping<TestOrder, String> DIRTY_MAPPING = JdbcMapping.<TestOrder, String>builder()
            .table("test_orders_dirty")
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
        JdbcClient.create(ds).sql(DIRTY_TABLE_DDL).update();
        factory = new JdbcDataStoreFactory(ds, ExecutionContextReader.threadLocal(), Clock.systemUTC());
    }

    @BeforeEach
    void clean() {
        JdbcClient.create(dataSource).sql("DELETE FROM test_orders").update();
        JdbcClient.create(dataSource).sql("DELETE FROM test_orders_dirty").update();
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

    private DataStore<TestOrder, String> dirtyStore() {
        return factory.create(DIRTY_MAPPING);
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

    /**
     * 反向自测（#8）：8 个线程同租户同主键同时首存，谁也不该收到唯一约束冲突。
     *
     * <p>为什么必须用真并发：冲突只发生在"UPDATE 影响 0 行"与"INSERT 落库"之间那一瞬，
     * 单线程造不出这个窗口（先手工插一行再 save，走的只是普通更新路径）——
     * 我第一版就是这么写的，名字叫"撞唯一约束"，实际一次也没撞上，是条假守门人。
     * 8 个线程经 barrier 同时发起，UPDATE 必然全部落空、INSERT 必然撞车，
     * 旧实现下有 7 个线程收到 DuplicateKeyException——实证已确认这条会红。
     */
    @Test
    void 并发首存_多线程同时写_无人因冲突失败() throws Exception {
        int n = 8;
        CyclicBarrier gate = new CyclicBarrier(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = i;
                futures.add(pool.submit(() -> as("u" + idx, "tenant-a", () -> {
                    try {
                        gate.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                        throw new IllegalStateException("并发起跑失败——测试脚手架问题，不是被测行为", e);
                    }
                    return store().save(TestOrder.create("o-concurrent", 100L + idx));
                })));
            }
            for (Future<?> f : futures) {
                try {
                    f.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    assertThat(e.getCause())
                            .as("并发首存是正常竞争，不该把数据库约束冲突抛给调用方")
                            .isNotInstanceOf(DuplicateKeyException.class)
                            .isNotInstanceOf(DataIntegrityViolationException.class);
                    throw new AssertionError("并发首存出现非预期异常", e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        TestOrder row = as("u0", "tenant-a", () -> store().findById("o-concurrent"))
                .orElseThrow(() -> new AssertionError("竞争结束后行必须存在"));
        assertThat(row.auditFields().createdBy())
                .as("created 归先到者——转更新路径不能把创建信息改成最后写入的人").startsWith("u");
        assertThat(row.auditFields().updatedBy()).as("updated 也要盖章，不能留空").isNotBlank();
    }

    /**
     * 反向自测（#9）：审计列为 NULL 时必须指认是哪张表、哪一列的哪个值，
     * 而不是让 {@code rs.getTimestamp(...).toInstant()} 裸抛 NPE——
     * 裸 NPE 没有任何上下文，排查只能从"哪里空了"开始猜。
     */
    @Test
    void 审计列NULL_大声指认表与列_不是裸NPE() {
        JdbcClient.create(dataSource).sql("""
                        INSERT INTO test_orders_dirty
                          (tenant, id, created_by, created_at, updated_by, updated_at, amount_cents)
                        VALUES ('tenant-a', 'o-dirty', 'alice', NULL, 'alice', ?, 1)""")
                .param(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")))
                .update();

        assertThatThrownBy(() -> as("bob", "tenant-a", () -> dirtyStore().save(TestOrder.create("o-dirty", 2L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test_orders_dirty")
                .hasMessageContaining("created_at")
                .hasMessageContaining("NULL");
    }

    @Test
    void 观察装饰可组合_opt_in() {
        List<ObservationEvent> events = new ArrayList<>();
        DataStore<TestOrder, String> observed = DataStores.observing(store(), TestOrder::id, events::add);
        as("alice", "tenant-a", () -> observed.save(TestOrder.create("o-1", 100L)));
        assertThat(events).anyMatch(e -> e.name().equals("data.saved"));
    }

    /**
     * 反向自测：工厂的读取器来自装配方注入，绝不回落到全局线程绑定。
     * 若有人把「无读取器时回落 ExecutionContextReader.threadLocal()」的捷径加回来，
     * 行会落进线程上绑的 tenant-a，前两条断言立刻变红。
     */
    @Test
    void 注入的读取器决定租户_不回落全局线程绑定() {
        ExecutionContextReader injected =
                () -> ExecutionContext.of(Operator.human("carol", "tenant-injected"), "t-injected");
        DataStore<TestOrder, String> injectedStore =
                new JdbcDataStoreFactory(dataSource, injected, Clock.systemUTC()).create(MAPPING);

        // 线程上绑的是 tenant-a，但写入必须落在注入读取器的租户里
        TestOrder saved = as("alice", "tenant-a", () -> injectedStore.save(TestOrder.create("o-inj", 100L)));
        assertThat(saved.auditFields().createdBy()).as("审计的操作者同样取自注入的读取器").isEqualTo("carol");

        assertThat(as("alice", "tenant-a", () -> store().findById("o-inj")))
                .as("线程绑定的 tenant-a 不该看到——行没落在它那儿").isEmpty();
        assertThat(as("carol", "tenant-injected", () -> store().findById("o-inj")))
                .as("注入读取器的租户才该看到").isPresent();
    }
}
