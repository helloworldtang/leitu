package cn.youhuale.leitu.capability.data.internal;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.model.PageRequest;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** 内存兜底实现。外部一律经 {@code DataStores.inMemory(...)} 获取，不直接实例化。 */
public final class InMemoryDataStore<T extends Auditable, ID>
        implements DataStore<T, ID> {

    private record RowKey(String tenant, Object id) {
    }

    private final Function<T, ID> idOf;

    private final ExecutionContextReader reader;

    private final Clock clock;

    private final ConcurrentHashMap<RowKey, T> rows = new ConcurrentHashMap<>();

    public InMemoryDataStore(Function<T, ID> idOf, ExecutionContextReader reader, Clock clock) {
        this.idOf = Objects.requireNonNull(idOf, "idOf 必填：内存实现按主键索引行——传 Order::id 这类方法引用");
        this.reader = Objects.requireNonNull(reader, "reader 必填：租户与操作者从执行上下文来——传 ExecutionContextReader.threadLocal()");
        this.clock = Objects.requireNonNull(clock, "clock 必填：审计时刻从存取器时钟来——测试传固定 Clock");
    }

    /**
     * 落库 = 一次原子 upsert。
     *
     * <p>为什么必须写成 {@code compute}：判定"行在不在"与"写哪一行"之间不能留窗口。
     * 早先是 {@code containsKey → 取旧行 → put} 三步（check-then-act），
     * 两个线程同时首存同一主键时可能双双判定"行不在"——于是双方都盖插入章，
     * 后写入的那份把先到者的创建信息（createdBy/createdAt）整体覆盖，
     * 而行实际早已存在。JDBC 侧靠唯一约束 + UPDATE/INSERT 序不会有这一幕
     * （见 JdbcDataStore），内存侧却"看运气"——同一段业务在两个实现上得到两种答案。
     *
     * <p>临界区里只有三件事：读旧行、算章、写字。此外无他——不调外部资源、不发观测，
     * 也不在实现内部回调本存取器。唯一的调用方代码是 {@code withAuditFields}：
     * 它是 {@link Auditable} 契约里的纯 wither（record 一行返回同型实体），不得有副作用。
     */
    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity 必填：无实体可存——先构造再 save");
        ID id = idOf.apply(entity);
        Objects.requireNonNull(id, "主键不能为 null：idOf 提取到 null——新建实体先落主键");
        String tenant = currentTenant();
        String operator = reader.current().operator().subject();
        RowKey key = key(tenant, id);

        return rows.compute(key, (ignored, existing) -> {
            // 行存在性定插改（实体自带章不作数，单一事实源）
            AuditFields next = existing == null
                    ? AuditFields.stampedBy(operator, clock.instant())
                    : existing.auditFields().restampedBy(operator, clock.instant());
            // Java 无自类型：协变返回保证此处强转运行期安全。
            @SuppressWarnings("unchecked")
            T stamped = (T) entity.withAuditFields(next);
            Objects.requireNonNull(stamped, "withAuditFields 不能返回 null：盖完章必须交回实体（record 一行 wither）");
            return stamped;
        });
    }

    @Override
    public Optional<T> findById(ID id) {
        Objects.requireNonNull(id, "id 必填：无主键无法定位行——缺失语义是 Optional.empty()，不是 null 入参");
        return Optional.ofNullable(rows.get(key(currentTenant(), id)));
    }

    @Override
    public boolean deleteById(ID id) {
        Objects.requireNonNull(id, "id 必填：无主键无法定位行——未见语义是 false，不是 null 入参");
        return rows.remove(key(currentTenant(), id)) != null;
    }

    @Override
    public List<T> findAll() {
        String tenant = currentTenant();
        return rows.entrySet().stream()
                .filter(e -> e.getKey().tenant().equals(tenant))
                .map(e -> e.getValue())
                .toList();
    }

    /**
     * 翻页：本租户的行按主键字符串升序排定，再跳过 offset、最多取 limit 条。
     *
     * <p>排序理由：没有确定顺序的翻页会重行或漏行（两页之间行集合发生位移时尤然）。
     * 这里用主键字符串升序——与 JDBC 侧的 {@code ORDER BY id} 同为"同一个 Key 空间上的确定序"，
     * 于是同一份数据在两个实现上翻出的页完全相同（两个模块各自断言"不重不漏"）。
     */
    @Override
    public List<T> findAll(PageRequest page) {
        Objects.requireNonNull(page, "page 必填：要哪一页说清楚——传 PageRequest.first(50)；取全部请用 findAll()");
        String tenant = currentTenant();
        return rows.entrySet().stream()
                .filter(e -> e.getKey().tenant().equals(tenant))
                .sorted(Comparator.comparing(e -> String.valueOf(e.getKey().id())))
                .map(e -> e.getValue())
                .skip(page.offset())
                .limit(page.limit())
                .toList();
    }

    private RowKey key(String tenant, Object id) {
        return new RowKey(tenant, id);
    }

    private String currentTenant() {
        return reader.current().operator().tenant();
    }

    /** 大声标注：内存实现重启即失，生产必换 adapter（分维度默认值：数据维度的最小可用兜底）。 */
    @Override
    public String toString() {
        return "DataStore{内存实现，重启即失——生产必换 adapter（见 docs/problems/data-access.md）}";
    }
}
