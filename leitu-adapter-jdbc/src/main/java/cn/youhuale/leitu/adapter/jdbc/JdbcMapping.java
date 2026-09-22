package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.model.Auditable;
import org.springframework.jdbc.core.RowMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * JDBC 行映射——adapter 的显式绑定面：表、业务列、行⇄实体的双向映射全部由使用方给出，
 * 零反射、零注解（拒隐式魔法）。
 *
 * <p>表约定（v1 固定，扩展留缝）：单表；列 tenant、id、created_by、created_at、updated_by、updated_at
 * 由 adapter 统一读写（见 {@link #RESERVED_COLUMNS}）；{@link #columns()} 是使用方的业务列（不含上述六列）。
 */
public final class JdbcMapping<T extends Auditable, ID> {

    /** 业务列值抽取：返回值顺序与 columns 一一对应（adapter 按此绑定 SQL 参数）。 */
    @FunctionalInterface
    public interface Values<T> {
        List<Object> read(T entity);
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * adapter 统一读写的六列——业务列不得与之重名（{@link #columns()} 只放业务列）。
     *
     * <p>为什么要在 build() 就拦：列名一旦相撞，SQL 里会出现两个同名列——
     * INSERT 报"列名重复"，SELECT 又是另一套行为，而且错误发生在第一次运行到那条语句时，
     * 距离写错的地方隔着整个装配过程。构造期挡住，代价是几微秒。
     */
    public static final List<String> RESERVED_COLUMNS =
            List.of("tenant", "id", "created_by", "created_at", "updated_by", "updated_at");

    private final String table;
    private final List<String> columns;
    private final Function<T, ID> idOf;
    private final Values<T> values;
    private final RowMapper<T> rowMapper;

    private JdbcMapping(String table, List<String> columns, Function<T, ID> idOf,
                        Values<T> values, RowMapper<T> rowMapper) {
        this.table = table;
        this.columns = List.copyOf(columns);
        this.idOf = idOf;
        this.values = values;
        this.rowMapper = rowMapper;
    }

    public static <T extends Auditable, ID> Builder<T, ID> builder() {
        return new Builder<>();
    }

    public String table() {
        return table;
    }

    public List<String> columns() {
        return columns;
    }

    public Function<T, ID> idOf() {
        return idOf;
    }

    public Values<T> values() {
        return values;
    }

    public RowMapper<T> rowMapper() {
        return rowMapper;
    }

    /** 构造器：校验在这里做一次（教学式失败），运行期零校验开销。 */
    public static final class Builder<T extends Auditable, ID> {
        private String table;
        private List<String> columns = List.of();
        private Function<T, ID> idOf;
        private Values<T> values = entity -> List.of();
        private RowMapper<T> rowMapper;

        public Builder<T, ID> table(String table) {
            this.table = table;
            return this;
        }

        public Builder<T, ID> columns(String... columns) {
            this.columns = List.of(columns);
            return this;
        }

        public Builder<T, ID> idOf(Function<T, ID> idOf) {
            this.idOf = idOf;
            return this;
        }

        public Builder<T, ID> values(Values<T> values) {
            this.values = values;
            return this;
        }

        public Builder<T, ID> rowMapper(RowMapper<T> rowMapper) {
            this.rowMapper = rowMapper;
            return this;
        }

        public JdbcMapping<T, ID> build() {
            Objects.requireNonNull(table, "table 必填：数据落在哪张表");
            if (!IDENTIFIER.matcher(table).matches()) {
                throw new IllegalArgumentException("table 必须是合法标识符（字母/下划线开头，仅字母数字下划线）：当前 \"" + table + "\"");
            }
            Set<String> seen = new LinkedHashSet<>();
            for (String column : columns) {
                Objects.requireNonNull(column, "列名不能为 null");
                if (!IDENTIFIER.matcher(column).matches()) {
                    throw new IllegalArgumentException("列名必须是合法标识符：当前 \"" + column + "\"");
                }
                String lower = column.toLowerCase(Locale.ROOT);
                // SQL 标识符通常大小写不敏感：id 与 ID、amount 与 Amount 落到一批方言里是同一列，
                // 只看字面量去重会放过这类相撞。
                if (!seen.add(lower)) {
                    throw new IllegalArgumentException("列名重复：\"" + column + "\" 在 columns 里出现了不止一次——"
                            + "两条业务列写同一个列名时 values() 的值会互相覆盖，SQL 也会报列名重复。"
                            + "修复：确认是不是复制粘贴多写了一列，或两列其实该合成一列；"
                            + "完整 columns=" + columns);
                }
                if (RESERVED_COLUMNS.contains(lower)) {
                    throw new IllegalArgumentException("列名 \"" + column + "\" 是 adapter 保留列："
                            + RESERVED_COLUMNS + " 由 adapter 统一读写（租户、主键、审计四件套），业务列不能占用。"
                            + "业务含义需要同名字段时改名（如 tenant_code / created_by_name），"
                            + "读这类值请在 rowMapper 里用别名或直接读保留列的值");
                }
            }
            Objects.requireNonNull(idOf, "idOf 必填：实体主键怎么取");
            Objects.requireNonNull(values, "values 必填：业务列值怎么抽（与 columns 一一对应）");
            Objects.requireNonNull(rowMapper, "rowMapper 必填：行怎么变回实体（审计列用 JdbcAudit.fields(rs) 读）");
            return new JdbcMapping<>(table, columns, idOf, values, rowMapper);
        }
    }
}
