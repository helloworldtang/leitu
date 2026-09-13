package cn.youhuale.leitu.capability.data.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 审计四件套——数据行的事实性元数据：谁建/何时建/谁改/何时改。
 *
 * <p>两态合法：全空（新实体未盖章，{@link #empty()}）或全满（存取器盖章）；半空=盖章逻辑漏了字段，
 * 构造期即失败。盖章算法只有一份（{@link #stampedBy} / {@link #restampedBy}），存取器与 adapter 共用。
 * 软删除（deletedBy/at）与乐观锁（version）是策略不是事实——留缝不入 v1（见 ADR-010）。
 */
public record AuditFields(String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {

    public AuditFields {
        int filled = (createdBy != null ? 1 : 0) + (createdAt != null ? 1 : 0)
                + (updatedBy != null ? 1 : 0) + (updatedAt != null ? 1 : 0);
        if (filled != 0 && filled != 4) {
            throw new IllegalArgumentException(
                    "审计四件套成对出现：createdBy/createdAt/updatedBy/updatedAt 要么全空"
                            + "（新实体用 AuditFields.empty()），要么全满（存取器盖章）；"
                            + "半空说明盖章逻辑漏了字段——见 docs/problems/data-access.md");
        }
    }

    /** 新实体：不带章。 */
    public static AuditFields empty() {
        return new AuditFields(null, null, null, null);
    }

    /** 已盖章？ */
    public boolean stamped() {
        return createdBy != null;
    }

    /** 插入章：四件套同源同刻（by=当前操作者，at=存取器时钟）。 */
    public static AuditFields stampedBy(String operator, Instant at) {
        Objects.requireNonNull(operator,
                "operator 必填：审计要回答\"谁\"——传 ExecutionContextReader.current().operator().subject()");
        if (operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空白：不知道是谁就别盖章");
        }
        Objects.requireNonNull(at, "at 必填：审计要回答\"何时\"——传存取器的 Clock.instant()");
        return new AuditFields(operator, at, operator, at);
    }

    /** 更新章：createdBy/createdAt 保留，updatedBy/updatedAt 换新。未盖章的行不能盖更新章（先插入落库）。 */
    public AuditFields restampedBy(String operator, Instant at) {
        if (!stamped()) {
            throw new IllegalArgumentException("未盖章的行不能盖更新章：插入章先落库（AuditFields.stampedBy），"
                    + "再谈更新——见 docs/problems/data-access.md");
        }
        Objects.requireNonNull(operator,
                "operator 必填：审计要回答\"谁\"——传 ExecutionContextReader.current().operator().subject()");
        if (operator.isBlank()) {
            throw new IllegalArgumentException("operator 不能为空白：不知道是谁就别盖章");
        }
        Objects.requireNonNull(at, "at 必填：审计要回答\"何时\"——传存取器的 Clock.instant()");
        return new AuditFields(createdBy, createdAt, operator, at);
    }
}
