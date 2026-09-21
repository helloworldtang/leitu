package cn.youhuale.leitu.adapter.jdbc.internal;

import cn.youhuale.leitu.adapter.jdbc.JdbcMapping;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
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
 * <p>save 以"行存在性"为准：先查已有行的 created 对——在则更新章（created 保留），
 * 不在则插入章（四件同源同刻）。并发写竞争由数据库唯一约束 (tenant, id) 兜底，冲突大声失败。
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

        Optional<AuditFields> existing = existingStamp(tenant, id);
        if (existing.isPresent()) {
            AuditFields stamped = existing.get().restampedBy(operator, now);
            jdbc.sql(updateSql()).param(operator).param(Timestamp.from(now))
                    .params(values).param(tenant).param(id).update();
            return stamped(entity, stamped);
        }
        AuditFields stamped = AuditFields.stampedBy(operator, now);
        jdbc.sql(insertSql()).param(tenant).param(id)
                .param(operator).param(Timestamp.from(now))
                .param(operator).param(Timestamp.from(now))
                .params(values).update();
        return stamped(entity, stamped);
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
                    String createdBy = rs.getString("created_by");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
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
