package cn.youhuale.leitu.capability.data.spi;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.model.PageRequest;

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
 *   <li><b>save 是一次 atomic upsert</b>：插入/更新的判定、盖章、写行必须一气呵成——
 *       check-then-act（先看有没有、再决定插入）会让并发首存的两个线程都走插入分支，
 *       先到者的创建信息被后到者整体覆盖。
 *       内存实现与 JDBC 实现的语义必须一致（两条路径互为对方的对照组）：
 *       任一处分叉，同一段业务在两条路径上会得到两种答案。</li>
 *   <li><b>列表可分页且顺序确定</b>：{@link #findAll(PageRequest)} 必须实现，
 *       并保证同一 Store 的排序稳定——相邻窗口连续翻完，每行恰好出现一次（不重不漏）。</li>
 * </ol>
 *
 * <p>真库（JDBC / MyBatis / JPA 绑定）由 adapter 实现本接口（盖章算法复用
 * AuditFields.stampedBy / restampedBy）；列表行过滤（WHERE 注入）、查询语言属
 * adapter 扩展（数据权限的单资源判定走判定链，见 docs/problems/data-access.md 四、边界）。
 */
public interface DataStore<T extends Auditable, ID> {

    /** 落库（插入或更新，以行是否已存在为准）并盖章；返回落库实体（带章）。 */
    T save(T entity);

    /** 按主键读当前租户的行；他租户或缺失 = Optional.empty()（不泄漏存在性）。 */
    Optional<T> findById(ID id);

    /** 删除当前租户的行；命中返回 true，未见（他租户/缺失）返回 false——不报错。 */
    boolean deleteById(ID id);

    /**
     * 当前租户的全部行——<b>离线/上限明确时才用</b>：租户数据在增长，接口层一律走
     * {@link #findAll(PageRequest)}（租户规模一年翻几番，全量列表会把整张表拖进内存）。
     * 顺序不承诺。
     */
    List<T> findAll();

    /**
     * 翻页取当前租户的行：从第 {@code page.offset()} 行起，最多 {@code page.limit()} 行。
     *
     * <p><b>顺序是实现必须给出的确定排序</b>（内存：主键字符串升序；JDBC：{@code ORDER BY id}）。
     * 没确定顺序的翻页会重行或漏行——可验收的等价说法：相邻窗口连续翻完，每行恰好出现一次。
     * 数据集合在此期间不被修改是前提（实时写入跨越窗口的重/漏不属本合同能排除的范畴）。
     */
    List<T> findAll(PageRequest page);
}
