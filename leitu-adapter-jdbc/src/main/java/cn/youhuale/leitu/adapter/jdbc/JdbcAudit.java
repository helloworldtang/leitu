package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.model.AuditFields;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 审计列读取助手：行映射器里读 adapter 管理的审计四列
 * （created_by / created_at / updated_by / updated_at）。
 */
public final class JdbcAudit {

    private JdbcAudit() {
    }

    public static AuditFields fields(ResultSet rs) throws SQLException {
        return new AuditFields(
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("updated_by"),
                rs.getTimestamp("updated_at").toInstant());
    }
}
