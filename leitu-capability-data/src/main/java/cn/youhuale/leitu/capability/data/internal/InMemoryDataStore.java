package cn.youhuale.leitu.capability.data.internal;

import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;

import java.time.Clock;
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

    @Override
    public T save(T entity) {
        Objects.requireNonNull(entity, "entity 必填：无实体可存——先构造再 save");
        ID id = idOf.apply(entity);
        Objects.requireNonNull(id, "主键不能为 null：idOf 提取到 null——新建实体先落主键");
        String tenant = currentTenant();
        String operator = reader.current().operator().subject();

        AuditFields current = entity.auditFields();
        AuditFields next = rows.containsKey(key(tenant, id))
                ? rows.get(key(tenant, id)).auditFields().restampedBy(operator, clock.instant())
                : AuditFields.stampedBy(operator, clock.instant());
        // 行存在性定插改（实体自带章不作数，单一事实源）。Java 无自类型：协变返回保证此处强转运行期安全。
        @SuppressWarnings("unchecked")
        T stamped = (T) entity.withAuditFields(next);
        Objects.requireNonNull(stamped, "withAuditFields 不能返回 null：盖完章必须交回实体（record 一行 wither）");
        rows.put(key(tenant, id), stamped);
        return stamped;
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
