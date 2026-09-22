package cn.youhuale.leitu.capability.data.internal;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.guard.api.AccessDeniedException;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 数据权限装饰器（opt-in）：每次读写前过一次判定链，否决即抛 {@link AccessDeniedException}（不静默跳过）。
 *
 * <p>解决的问题：数据权限此前只有"自觉"这一条路径——金样本教的是
 * {@code if (d.isAllow()) orders.save(...)}，漏写一次判定就是一次越权，而漏写编译得过、测试也过得去。
 * 本装饰器把判定收口到存取缝上：包了就每次都判，没包就明说没包。
 *
 * <p>判定动作固定四件（点分命名，检索键）：{@code data:save} / {@code data:read} /
 * {@code data:delete} / {@code data:list}；资源名 = {@code 域/主键}（列表为域级）。
 * 主体取 {@code ExecutionContextReader.current().operator().subject()}。
 *
 * <p><b>大声提醒</b>：{@link GuardChain#of()} 空链 = deny-by-default——包了装饰器却不注册任何
 * Guard，会得到"一切读写都被否决"，这是设计意图（未声明权限 = 不允许），不是故障。
 * 行级过滤（列表只返回可见行）属 adapter 扩展，不在本装饰器内——它只做单资源判定。
 */
public final class GuardedDataStore<T extends Auditable, ID> implements DataStore<T, ID> {

    public static final String ACTION_SAVE = "data:save";

    public static final String ACTION_READ = "data:read";

    public static final String ACTION_DELETE = "data:delete";

    public static final String ACTION_LIST = "data:list";

    private final DataStore<T, ID> delegate;

    private final GuardChain chain;

    private final String domain;

    private final Function<T, ID> idOf;

    private final ExecutionContextReader reader;

    public GuardedDataStore(DataStore<T, ID> delegate, GuardChain chain, String domain,
                            Function<T, ID> idOf, ExecutionContextReader reader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate 必填：装饰的是哪个存取器");
        this.chain = Objects.requireNonNull(chain, "chain 必填：数据权限走哪条判定链");
        this.domain = Objects.requireNonNull(domain, "domain 必填：资源名前缀（如 \"order\"）——判定的资源标识从它拼出");
        if (domain.isBlank()) {
            throw new IllegalArgumentException("domain 不能为空白：资源名前缀（如 \"order\"）");
        }
        this.idOf = Objects.requireNonNull(idOf, "idOf 必填：资源标识要带主键——传 Order::id");
        this.reader = Objects.requireNonNull(reader, "reader 必填：判定主体（谁在操作）从执行上下文来");
    }

    @Override
    public T save(T entity) {
        check(ACTION_SAVE, resourceOf(idOf.apply(entity)));
        return delegate.save(entity);
    }

    @Override
    public Optional<T> findById(ID id) {
        check(ACTION_READ, resourceOf(id));
        return delegate.findById(id);
    }

    @Override
    public boolean deleteById(ID id) {
        check(ACTION_DELETE, resourceOf(id));
        return delegate.deleteById(id);
    }

    @Override
    public List<T> findAll() {
        check(ACTION_LIST, domain);
        return delegate.findAll();
    }

    /**
     * 判定放行/否决的唯一出口：否决即抛，绝不降级为"空结果"。
     *
     * <p>不降级的理由：{@code findById} 对"他租户的行"返回 empty 是<b>不泄漏存在性</b>，
     * 而"无权访问"若也返回 empty，就把"没权限"伪装成"不存在"——调用方无从分辨，
     * 排障时只能靠猜。两者语义不同，出口就必须不同。
     */
    private void check(String action, String resource) {
        Decision decision = chain.check(AccessRequest.inbound(
                reader.current().operator().subject(), action, resource));
        if (decision.isDeny()) {
            throw AccessDeniedException.from(decision, reader);
        }
    }

    private String resourceOf(ID id) {
        return domain + "/" + id;
    }

    @Override
    public String toString() {
        return "Guarded[" + domain + "]" + delegate;
    }
}
