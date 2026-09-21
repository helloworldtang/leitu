package com.example.taskservice;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;

/** 业务实体：record + 一行 wither（照 docs/problems/data-access.md 与金样本的姿势）。 */
public record Task(String id, AuditFields auditFields, String title) implements Auditable {

    public static Task create(String id, String title) {
        return new Task(id, AuditFields.empty(), title);
    }

    @Override
    public Task withAuditFields(AuditFields auditFields) {
        return new Task(id, auditFields, title);
    }
}
