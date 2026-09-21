package cn.youhuale.leitu.examples.springboot;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;

/** 范例实体：record + 一行 wither 实现 {@link Auditable}（与 data-access 金样本同款姿势）。 */
public record Order(String id, AuditFields auditFields, long amountCents) implements Auditable {

    public static Order create(String id, long amountCents) {
        return new Order(id, AuditFields.empty(), amountCents);
    }

    @Override
    public Order withAuditFields(AuditFields auditFields) {
        return new Order(id, auditFields, amountCents);
    }
}
