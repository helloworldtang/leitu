package cn.youhuale.leitu.capability.data.api;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
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

    /** 内存兜底（大声标注：重启即失，生产必换 adapter）；时钟取系统 UTC。 */
    public static <T extends Auditable, ID> DataStore<T, ID> inMemory(Function<T, ID> idOf) {
        return inMemory(idOf, Clock.systemUTC());
    }

    /** 内存兜底，可注入时钟（测试断言审计时刻用）。 */
    public static <T extends Auditable, ID> DataStore<T, ID> inMemory(Function<T, ID> idOf, Clock clock) {
        return new cn.youhuale.leitu.capability.data.internal.InMemoryDataStore<>(
                idOf, ExecutionContextReader.threadLocal(), clock);
    }

    /** 观察装饰（opt-in，默认不记）：给任一存取器记 data.* 观测事件（data.saved / data.read.hit / …）。 */
    public static <T extends Auditable, ID> DataStore<T, ID> observing(
            DataStore<T, ID> store, Function<T, ID> idOf, ObservationRecorder recorder) {
        return new cn.youhuale.leitu.capability.data.internal.ObservingDataStore<>(
                store, idOf, recorder, ExecutionContextReader.threadLocal());
    }
}
