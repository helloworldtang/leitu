package cn.youhuale.leitu.capability.data.spi;

import cn.youhuale.leitu.capability.data.model.Auditable;

import java.util.List;
import java.util.Optional;

/**
 * 业务数据的存取缝——"业务数据怎么读写"的标准答案面（见 ADR-010）。
 *
 * <p>合同（实现者必须守住；ArchUnit 看不见 SQL——合同 + 金样本双保险）：
 * <ol>
 *   <li><b>租户作用域</b>：一切读写只作用于当前上下文的租户
 *       （ExecutionContextReader.current().operator().tenant()）。他租户的行不可见——
 *       findById 返回 Optional.empty() 而非报错（不泄漏存在性）；"-" 是系统级作用域，
 *       不是全局通配——系统行只对 "-" 上下文可见。</li>
 *   <li><b>审计盖章</b>：save 落库前由存取器盖章——行不存在=插入章（四件套全新）；
 *       行已存在=更新章（createdBy/createdAt 从已存行保留）。盖章权在存取器：
 *       实体自带的章不作数，以行存在性为准重落（单一事实源）。</li>
 *   <li><b>主键在租户作用域内唯一</b>：跨租户同 id 是两行（行归属在写入时定）。</li>
 * </ol>
 *
 * <p>真库（JDBC / MyBatis / JPA 绑定）由 adapter 实现本接口（盖章算法复用
 * AuditFields.stampedBy / restampedBy）；列表行过滤（WHERE 注入）、分页、查询语言属
 * adapter 扩展（数据权限的单资源判定走判定链，见 docs/problems/data-access.md 四、边界）。
 */
public interface DataStore<T extends Auditable, ID> {

    /** 落库（插入或更新，以行是否已存在为准）并盖章；返回落库实体（带章）。 */
    T save(T entity);

    /** 按主键读当前租户的行；他租户或缺失 = Optional.empty()（不泄漏存在性）。 */
    Optional<T> findById(ID id);

    /** 删除当前租户的行；命中返回 true，未见（他租户/缺失）返回 false——不报错。 */
    boolean deleteById(ID id);

    /** 当前租户的全部行（顺序不承诺；过滤/分页属 adapter 扩展）。 */
    List<T> findAll();
}
