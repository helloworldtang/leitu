package cn.youhuale.leitu.examples.data;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;

/**
 * 范例实体：record + 一行 wither 实现 {@link Auditable}——照这个样子写你的业务实体。
 *
 * <p>新实体不带章（create 传 AuditFields.empty()）；盖章时机与内容归存取器（见 docs/problems/data-access.md）。
 */
public record Order(String id, AuditFields auditFields, long amountCents) implements Auditable {

    public static Order create(String id, long amountCents) {
        return new Order(id, AuditFields.empty(), amountCents);
    }

    @Override
    public Order withAuditFields(AuditFields auditFields) {
        return new Order(id, auditFields, amountCents);
    }
}
