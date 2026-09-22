package cn.youhuale.leitu.capability.data.api;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;

import java.time.Clock;
import java.util.function.Function;

/**
 * 数据存取器的工厂——业务/宿主从 api 取实现，不触碰 internal。
 *
 * <p>用法：
 * <pre>{@code
 * DataStore<Order, String> orders = DataStores.inMemory(Order::id);          // 兜底：内存实现，重启即失
 * DataStore<Order, String> observed = DataStores.observing(orders,
 *         Order::id, ObservationRecorder.of());                              // opt-in 观测装饰
 * }</pre>
 *
 * <p>真库由 adapter 实现 {@link DataStore}（见 docs/problems/data-access.md）。
 */
public final class DataStores {

    private DataStores() {
    }

    /** 内存兜底（大声标注：重启即失，生产必换 adapter）；时钟取系统 UTC，读取器回落全局线程绑定。 */
    public static <T extends Auditable, ID> DataStore<T, ID> inMemory(Function<T, ID> idOf) {
        return inMemory(idOf, Clock.systemUTC());
    }

    /** 内存兜底，可注入时钟（测试断言审计时刻用）。 */
    public static <T extends Auditable, ID> DataStore<T, ID> inMemory(Function<T, ID> idOf, Clock clock) {
        return inMemory(idOf, ExecutionContextReader.threadLocal(), clock);
    }

    /**
     * 内存兜底，读取器与时钟全部由装配方给出——<b>非 Spring 场景或自定义绑定机制走这个重载</b>。
     *
     * <p>读取器决定租户作用域：本库不替调用方猜"谁在操作"。两参/单参重载回落到全局线程绑定，
     * 那只在默认线程级 binder 下才成立；换成 MDC、Reactor、网关透传就该用本重载显式给出。
     */
    public static <T extends Auditable, ID> DataStore<T, ID> inMemory(
            Function<T, ID> idOf, ExecutionContextReader reader, Clock clock) {
        return new cn.youhuale.leitu.capability.data.internal.InMemoryDataStore<>(idOf, reader, clock);
    }

    /**
     * 数据权限装饰（opt-in）：读写前过判定链，否决即抛 {@code AccessDeniedException}。
     *
     * <p>数据权限此前只有"自觉"一条路（金样本教手写 {@code if (d.isAllow())}，漏写即越权且编译得过）；
     * 包了本装饰器，四种读写各自带判定动作（data:save / data:read / data:delete / data:list），
     * 资源名 = {@code 域/主键}。<b>空链 = deny-by-default</b>——包了却不注册 Guard，一切读写被否决，
     * 这是设计意图（未声明权限 = 不允许）。
     *
     * @param domain 资源名前缀（如 {@code "order"}）
     * @param idOf   主键提取（如 {@code Order::id}）——资源标识要带主键
     */
    public static <T extends Auditable, ID> DataStore<T, ID> guarded(
            DataStore<T, ID> store, GuardChain chain, String domain, Function<T, ID> idOf) {
        return guarded(store, chain, domain, idOf, ExecutionContextReader.threadLocal());
    }

    /** 数据权限装饰，读取器由装配方给出（与 {@link #inMemory(Function, ExecutionContextReader, Clock)} 同款契约）。 */
    public static <T extends Auditable, ID> DataStore<T, ID> guarded(
            DataStore<T, ID> store, GuardChain chain, String domain,
            Function<T, ID> idOf, ExecutionContextReader reader) {
        return new cn.youhuale.leitu.capability.data.internal.GuardedDataStore<>(
                store, chain, domain, idOf, reader);
    }

    /** 观察装饰（opt-in，默认不记）：给任一存取器记 data.* 观测事件（data.saved / data.read.hit / …）。 */
    public static <T extends Auditable, ID> DataStore<T, ID> observing(
            DataStore<T, ID> store, Function<T, ID> idOf, ObservationRecorder recorder) {
        return observing(store, idOf, recorder, ExecutionContextReader.threadLocal());
    }

    /** 观察装饰，读取器由装配方给出（与 {@link #inMemory(Function, ExecutionContextReader, Clock)} 同款契约）。 */
    public static <T extends Auditable, ID> DataStore<T, ID> observing(
            DataStore<T, ID> store, Function<T, ID> idOf, ObservationRecorder recorder,
            ExecutionContextReader reader) {
        return new cn.youhuale.leitu.capability.data.internal.ObservingDataStore<>(
                store, idOf, recorder, reader);
    }
}
