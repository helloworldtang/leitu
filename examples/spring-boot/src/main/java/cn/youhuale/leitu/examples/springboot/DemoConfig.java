package cn.youhuale.leitu.examples.springboot;

import cn.youhuale.leitu.adapter.jdbc.JdbcAudit;
import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.adapter.jdbc.JdbcMapping;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 装配示范：真库存取器一行声明（显式映射，零反射）；守卫以 Bean 形式声明（自动收编进判定链）。
 */
@Configuration
public class DemoConfig {

    /** 显式映射：表 / 业务列 / 行⇄实体——租户列与审计四列由 adapter 统一管理。 */
    static final JdbcMapping<Order, String> ORDER_MAPPING = JdbcMapping.<Order, String>builder()
            .table("orders")
            .columns("amount_cents")
            .idOf(Order::id)
            .values(o -> List.of(o.amountCents()))
            .rowMapper((rs, rowNum) -> new Order(
                    rs.getString("id"), JdbcAudit.fields(rs), rs.getLong("amount_cents")))
            .build();

    @Bean
    DataStore<Order, String> orders(JdbcDataStoreFactory factory) {
        return factory.create(ORDER_MAPPING);
    }

    /** 演示守卫：order.create 仅 alice 放行——action 与 operationId 点分同源（ADR-013 增值点 3）。 */
    @Bean
    Guard orderCreateGuard() {
        return request -> "order.create".equals(request.action()) && !"alice".equals(request.subject())
                ? Decision.deny("仅 alice 可创建订单（演示守卫）", Retry.never())
                : Decision.allow();
    }
}
