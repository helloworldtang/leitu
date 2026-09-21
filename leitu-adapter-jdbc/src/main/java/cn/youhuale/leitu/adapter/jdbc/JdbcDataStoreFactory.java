package cn.youhuale.leitu.adapter.jdbc;

import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Objects;

/**
 * JdbcDataStore 的装配工厂：绑定一个 DataSource（+ 读取器与时钟），按映射产出 DataStore。
 *
 * <p>使用（一行声明，显式优先——ADR-014）：
 * <pre>{@code
 * @Bean
 * DataStore<Order, String> orders(JdbcDataStoreFactory factory) {
 *     return factory.create(ORDER_MAPPING);
 * }
 * }</pre>
 */
public final class JdbcDataStoreFactory {

    private final JdbcClient jdbc;
    private final ExecutionContextReader reader;
    private final Clock clock;

    public JdbcDataStoreFactory(DataSource dataSource) {
        this(dataSource, ExecutionContextReader.threadLocal(), Clock.systemUTC());
    }

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
