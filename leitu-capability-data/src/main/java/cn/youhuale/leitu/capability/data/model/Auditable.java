package cn.youhuale.leitu.capability.data.model;

/**
 * 实体自证"如何携带审计"的最小缝——业务实体实现它，存取器负责盖章。
 *
 * <p>实体只声明携带方式，不声明盖章时机与内容（那是 DataStore 的合同，见 ADR-010）。
 * record 一行 wither 即实现（协变返回具体类型）：
 * <pre>{@code
 * public record Order(String id, AuditFields auditFields, long amountCents) implements Auditable {
 *     public static Order create(String id, long amountCents) {
 *         return new Order(id, AuditFields.empty(), amountCents);   // 新实体不带章
 *     }
 *
 *     @Override
 *     public Order withAuditFields(AuditFields auditFields) {
 *         return new Order(id, auditFields, amountCents);           // 盖章=换新实体
 *     }
 * }
 * }</pre>
 */
public interface Auditable {

    /** 当前审计章（新实体 = {@link AuditFields#empty()}）。 */
    AuditFields auditFields();

    /** 盖章拷贝：返回携带新章的同义实体（record 用协变返回具体类型）。 */
    Auditable withAuditFields(AuditFields auditFields);
}
