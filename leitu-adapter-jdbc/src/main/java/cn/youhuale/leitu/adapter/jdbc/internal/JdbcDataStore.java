package cn.youhuale.leitu.adapter.jdbc.internal;

import cn.youhuale.leitu.adapter.jdbc.JdbcMapping;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * DataStore 的 JDBC 实现（内部）：落实三条合同——租户作用域（WHERE tenant = ?）、
 * 审计盖章（复用 AuditFields.stampedBy / restampedBy，算法单源）、主键在租户作用域内唯一（(tenant, id)）。
 *
 * <p>save 是 upsert：先 UPDATE（行在则改、created 保留），影响 0 行才 INSERT（四件同源同刻）。
 * 两个并发首存必然撞上唯一约束 (tenant, id)——撞上的一方转为更新路径，创建信息归先到者，
 * 而不是把 DuplicateKey 抛给调用方（并发首存是正常竞争，不是错误）。
 *
 * <p>外部一律经 {@code JdbcDataStoreFactory.create(...)} 获取，不直接实例化。
 */
public final class JdbcDataStore<T extends Auditable, ID> implements DataStore<T, ID> {

    private static final List<String> AUDIT_COLUMNS =
            List.of("created_by", "created_at", "updated_by", "updated_at");

    private final JdbcClient jdbc;
    private final JdbcMapping<T, ID> mapping;
    private final ExecutionContextReader reader;
    private final Clock clock;

    public JdbcDataStore(JdbcClient jdbc, JdbcMapping<T, ID> mapping, ExecutionContextReader reader, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc 必填");
        this.mapping = Objects.requireNonNull(mapping, "mapping 必填");
        this.reader = Objects.requireNonNull(reader, "reader 必填");
        this.clock = Objects.requireNonNull(clock, "clock 必填");
    }

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity 必填");
        ID id = Objects.requireNonNull(mapping.idOf().apply(entity), "idOf 返回 null：实体主键必填");
        String tenant = tenant();
        String operator = operator();
        Instant now = clock.instant();

        List<Object> values = mapping.values().read(entity);
        if (values.size() != mapping.columns().size()) {
            throw new IllegalStateException("values 与 columns 数量不一致：columns=" + mapping.columns().size()
                    + "，values=" + values.size() + "（显式映射错位是装配错误，大声失败）");
        }

        Optional<AuditFields> updated = updateExisting(tenant, id, values, operator, now);
        if (updated.isPresent()) {
            return stamped(entity, updated.get());
        }
        try {
            insert(tenant, id, values, operator, now);
        } catch (DuplicateKeyException e) {
            // 并发首存：另一线程刚写入同一 (tenant, id)。这不是错误——转更新路径，
            // created 归先到者，后到者只盖 updated 章。抛给调用方会让"并发首次写入"变成随机失败。
            AuditFields restamped = updateExisting(tenant, id, values, operator, now)
                    .orElseThrow(() -> new IllegalStateException(
                            "并发写冲突后转更新仍影响 0 行：行 [" + tenant + "/" + id
                                    + "] 在冲突瞬间被并发删除——重试本次 save", e));
            return stamped(entity, restamped);
        }
        return stamped(entity, AuditFields.stampedBy(operator, now));
    }

    /** 更新既有行并返回盖好更新章的字段；行不在则 empty（调用方据此改走插入）。 */
    private Optional<AuditFields> updateExisting(String tenant, ID id, List<Object> values,
                                                String operator, Instant now) {
        int rows = jdbc.sql(updateSql()).param(operator).param(Timestamp.from(now))
                .params(values).param(tenant).param(id).update();
        if (rows == 0) {
            return Optional.empty();
        }
        // 更新已生效却读不到 created 对：只可能是行在更新与回读之间被并发删除——
        // 此时无从追溯创建信息，退回插入章，不假装知道 created 是谁。
        AuditFields existing = existingStamp(tenant, id)
                .orElseGet(() -> AuditFields.stampedBy(operator, now));
        return Optional.of(existing.restampedBy(operator, now));
    }

    private void insert(String tenant, ID id, List<Object> values, String operator, Instant now) {
        jdbc.sql(insertSql()).param(tenant).param(id)
                .param(operator).param(Timestamp.from(now))
                .param(operator).param(Timestamp.from(now))
                .params(values).update();
    }

    // Java 无自类型：协变返回保证此处强转运行期安全（与 InMemoryDataStore 同款约定）。
    @SuppressWarnings("unchecked")
    private T stamped(T entity, AuditFields fields) {
        T result = (T) entity.withAuditFields(fields);
        Objects.requireNonNull(result, "withAuditFields 不能返回 null：盖完章必须交回实体（record 一行 wither）");
        return result;
    }

    @Override
    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "id 必填");
        return jdbc.sql(selectByIdSql()).param(tenant()).param(id)
                .query(mapping.rowMapper()).optional();
    }

    @Override
    public boolean deleteById(ID id) {
        Objects.requireNonNull(id, "id 必填");
        int rows = jdbc.sql(deleteSql()).param(tenant()).param(id).update();
        return rows > 0;
    }

    @Override
    public List<T> findAll() {
        return jdbc.sql(selectAllSql()).param(tenant()).query(mapping.rowMapper()).list();
    }

    // ---- SQL 组装（表/列名在 JdbcMapping 构造期已做标识符校验） ----

    private String readColumnsSql() {
        List<String> cols = new ArrayList<>();
        cols.add("id");
        cols.addAll(mapping.columns());
        cols.addAll(AUDIT_COLUMNS);
        return String.join(", ", cols);
    }

    private String selectByIdSql() {
        return "SELECT " + readColumnsSql() + " FROM " + mapping.table() + " WHERE tenant = ? AND id = ?";
    }

    private String selectAllSql() {
        return "SELECT " + readColumnsSql() + " FROM " + mapping.table() + " WHERE tenant = ?";
    }

    private String insertSql() {
        List<String> cols = new ArrayList<>();
        cols.add("tenant");
        cols.add("id");
        cols.addAll(AUDIT_COLUMNS);
        cols.addAll(mapping.columns());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            placeholders.append(i == 0 ? "?" : ", ?");
        }
        return "INSERT INTO " + mapping.table() + " (" + String.join(", ", cols) + ") VALUES (" + placeholders + ")";
    }

    private String updateSql() {
        StringBuilder sets = new StringBuilder("updated_by = ?, updated_at = ?");
        for (String column : mapping.columns()) {
            sets.append(", ").append(column).append(" = ?");
        }
        return "UPDATE " + mapping.table() + " SET " + sets + " WHERE tenant = ? AND id = ?";
    }

    private String deleteSql() {
        return "DELETE FROM " + mapping.table() + " WHERE tenant = ? AND id = ?";
    }

    private String existingStampSql() {
        return "SELECT created_by, created_at FROM " + mapping.table() + " WHERE tenant = ? AND id = ?";
    }

    private Optional<AuditFields> existingStamp(String tenant, ID id) {
        return jdbc.sql(existingStampSql()).param(tenant).param(id)
                .query((rs, rowNum) -> {
                    // 审计列 NULL 必须大声：这里若裸 NPE，调用方只能看到
                    // "NullPointerException" 而无上下文——真因（表缺审计列 / 历史行未回填）要自己猜。
                    Timestamp createdAtTs = rs.getTimestamp("created_at");
                    if (createdAtTs == null) {
                        throw new IllegalStateException("表 " + mapping.table() + " 的 created_at 为 NULL"
                                + "（tenant=" + tenant + ", id=" + id + "）：更新路径靠它保留创建信息，读不到就无法盖章。"
                                + "修复二选一——① 给 created_at 加 NOT NULL 并回填历史行；"
                                + "② 该行本就是脏数据，清掉后重新写入");
                    }
                    String createdBy = rs.getString("created_by");
                    Instant createdAt = createdAtTs.toInstant();
                    // 借"全满"合法态承载已有行的 created 对，随后 restampedBy 换新 updated 对（盖章算法单源复用）
                    return new AuditFields(createdBy, createdAt, createdBy, createdAt);
                })
                .optional();
    }

    private String tenant() {
        return reader.current().operator().tenant();
    }

    private String operator() {
        return reader.current().operator().subject();
    }
}
