package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 行映射的装配期校验（#10）：列名是客户给的自由文本，错到 SQL 里只知道"列名重复"，
 * 却不知道是哪个 binding 写错——错误必须在 build() 就指着那个列名说话。
 */
class JdbcMappingTest {

    record Thing(String id, AuditFields auditFields, String name) implements Auditable {
        @Override
        public Thing withAuditFields(AuditFields auditFields) {
            return new Thing(id, auditFields, name);
        }
    }

    private static JdbcMapping.Builder<Thing, String> builder() {
        return JdbcMapping.<Thing, String>builder()
                .table("things")
                .idOf(Thing::id)
                .values(t -> List.of(t.name()))
                .rowMapper((rs, rowNum) -> new Thing(
                        rs.getString("id"), JdbcAudit.fields(rs), rs.getString("name")));
    }

    @Test
    void 正常装配通过() {
        JdbcMapping<Thing, String> mapping = builder().columns("name").build();
        assertThat(mapping.columns()).containsExactly("name");
        assertThat(mapping.table()).isEqualTo("things");
    }

    @Test
    void 业务列撞保留列_构造期指名哪一列() {
        assertThatThrownBy(() -> builder().columns("tenant").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant")
                .hasMessageContaining("保留列");
    }

    @Test
    void 审计四件套也不能当作业务列() {
        for (String reserved : List.of("created_by", "created_at", "updated_by", "updated_at", "id")) {
            assertThatThrownBy(() -> builder().columns(reserved).build(),
                            "列名 " + reserved + " 属 adapter 保留")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(reserved);
        }
    }

    @Test
    void 大小写不同的同名保留列一样拦下() {
        assertThatThrownBy(() -> builder().columns("ID").build())
                .as("SQL 标识符多数方言大小写不敏感：ID 兜不掉这条检查")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID");
    }

    @Test
    void 业务列重复_构造期即失败() {
        assertThatThrownBy(() -> builder().columns("name", "name").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void 业务列仅大小写不同也算重复() {
        assertThatThrownBy(() -> builder().columns("name", "NAME").build())
                .as("落到数据库是同一列，values 会互相覆盖")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }
}
