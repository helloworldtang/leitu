package com.example.bookcatalog;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;

/** 业务实体：record + 一行 wither（照 docs/problems/data-access.md 与金样本的姿势）。 */
public record Book(String id, AuditFields auditFields, String title, long priceCents) implements Auditable {

    public static Book create(String id, String title, long priceCents) {
        return new Book(id, AuditFields.empty(), title, priceCents);
    }

    @Override
    public Book withAuditFields(AuditFields auditFields) {
        return new Book(id, auditFields, title, priceCents);
    }
}
