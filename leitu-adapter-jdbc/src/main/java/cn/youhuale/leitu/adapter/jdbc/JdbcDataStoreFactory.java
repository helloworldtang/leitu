package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Objects;

/**
 * JdbcDataStore 的装配工厂：绑定一个 DataSource + 读取器 + 时钟，按映射产出 DataStore。
 *
 * <p>使用（一行声明，显式优先——ADR-014）：
 * <pre>{@code
 * @Bean
 * DataStore<Order, String> orders(JdbcDataStoreFactory factory) {
 *     return factory.create(ORDER_MAPPING);
 * }
 * }</pre>
 *
 * <p><b>读取器必须由装配方注入，本类不提供任何回落到
 * {@code ExecutionContextReader.threadLocal()} 的捷径</b>：租户是数据作用域的唯一来源，
 * 一旦工厂自行回落到全局线程绑定，应用自定义的 {@code ExecutionContextBinder}
 * （异步、MDC、网关透传……）将在入口侧生效而存取器侧失效——行被静默写进匿名租户 "-"，
 * 不报错、不告警、只在跨租户串味时才被发现。宁可装配期大声失败，不可运行期静默错租户
 * （ADR-015；与「分维度默认值」无关——租户不属于可以给默认值的维度）。
 */
public final class JdbcDataStoreFactory {

    private final JdbcClient jdbc;
    private final ExecutionContextReader reader;
    private final Clock clock;

    public JdbcDataStoreFactory(DataSource dataSource, ExecutionContextReader reader, Clock clock) {
        Objects.requireNonNull(dataSource, "dataSource 必填");
        this.jdbc = JdbcClient.create(dataSource);
        this.reader = Objects.requireNonNull(reader, "reader 必填");
        this.clock = Objects.requireNonNull(clock, "clock 必填");
    }

    public <T extends Auditable, ID> DataStore<T, ID> create(JdbcMapping<T, ID> mapping) {
        return new cn.youhuale.leitu.adapter.jdbc.internal.JdbcDataStore<>(jdbc, mapping, reader, clock);
    }
}
