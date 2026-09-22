package cn.youhuale.leitu.examples.data;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.capability.data.api.DataStores;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.AccessDeniedException;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定 data 能力的标准用法语义。capability 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    private static final cn.youhuale.leitu.core.context.spi.ExecutionContextBinder BINDER =
            ExecutionContextBinders.threadLocal();

    @Test
    void 审计四件套自动盖章_alice建bob改() {
        DataStore<Order, String> orders = DataStores.inMemory(Order::id);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            orders.save(Order.create("order-42", 9900L));
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("bob", "tenant-a"), "t-2"))) {
            orders.save(Order.create("order-42", 12800L));
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-3"))) {
            AuditFields a = orders.findById("order-42").orElseThrow().auditFields();
            assertEquals("alice", a.createdBy(), "createdBy 永不改变");
            assertEquals("bob", a.updatedBy(), "updatedBy 换最后修改者");
        }
    }

    @Test
    void 租户隔离_同主键他租户不可见不泄漏() {
        DataStore<Order, String> orders = DataStores.inMemory(Order::id);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            orders.save(Order.create("order-42", 9900L));
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("mallory", "tenant-b"), "t-2"))) {
            assertTrue(orders.findById("order-42").isEmpty(), "不可见");
            assertTrue(orders.findAll().isEmpty(), "列表也不可见");
            assertFalse(orders.deleteById("order-42"), "删除=未见=false，不报错");
        }
    }

    /**
     * 列表的标准用法是分页：接口层一律 {@code findAll(PageRequest)}，
     * 全量 findAll() 留给离线任务——AI 照抄金样本时不该抄到 unbounded 那一条。
     */
    @Test
    void 列表走分页_连续翻完不重不漏() {
        DataStore<Order, String> orders = DataStores.inMemory(Order::id);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            orders.save(Order.create("order-a", 1L));
            orders.save(Order.create("order-b", 2L));
            orders.save(Order.create("order-c", 3L));
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-2"))) {
            List<String> seen = new ArrayList<>();
            cn.youhuale.leitu.capability.data.model.PageRequest[] cursor =
                    {cn.youhuale.leitu.capability.data.model.PageRequest.first(2)};
            for (int i = 0; i < 2; i++) {
                final cn.youhuale.leitu.capability.data.model.PageRequest page = cursor[0];
                orders.findAll(page).forEach(o -> seen.add(o.id()));
                cursor[0] = page.next();
            }
            assertEquals(List.of("order-a", "order-b", "order-c"), seen,
                    "两页翻完 = 本租户全量，且一行只出现一次");
        }
    }

    @Test
    void 系统作用域_系统行不对租户可见() {
        DataStore<Order, String> orders = DataStores.inMemory(Order::id);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.system(), "t-sys"))) {
            orders.save(Order.create("order-sys", 1L));
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertTrue(orders.findById("order-sys").isEmpty(), "\"-\" 不是全局通配");
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.system(), "t-sys"))) {
            assertTrue(orders.findById("order-sys").isPresent(), "\"-\" 上下文可见自己的行");
        }
    }

    /**
     * 数据权限收口到存取缝：包了装饰器，四种读写各自带判定，漏判在机制上不可能。
     *
     * <p>此前金样本教的是手写 {@code if (d.isAllow()) orders.save(...)}——漏写一次判定就是一次越权，
     * 而漏写编译得过、测试也过得去。现在判定在存取缝上，否决即抛，不靠自觉。
     */
    @Test
    void 数据权限收口到存取缝_否决即抛且不落库() {
        DataStore<Order, String> orders = DataStores.inMemory(Order::id);
        RoleMap roles = RoleMap.create()
                .subject("alice").hasRoles("admin")
                .subject("bob").hasRoles("member")
                .action("data:save").allowsRoles("admin")
                .build();
        GuardChain guard = GuardChain.of(AccessGuards.roleMap(roles));
        DataStore<Order, String> guarded = DataStores.guarded(orders, guard, "order", Order::id);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("bob", "tenant-a"), "t-1"))) {
            AccessDeniedException e = assertThrows(AccessDeniedException.class,
                    () -> guarded.save(Order.create("order-77", 100L)));
            assertTrue(e.getMessage().contains("data:save"), "否决要说清是哪个动作被拦：" + e.getMessage());
            assertTrue(orders.findById("order-77").isEmpty(), "否决即拦下：底层一行都没写");
        }
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-2"))) {
            guarded.save(Order.create("order-77", 100L));
            assertTrue(orders.findById("order-77").isPresent(), "放行即落库");
        }
    }

    @Test
    void 观察装饰_data事件带完整锚点() {
        List<ObservationEvent> events = new ArrayList<>();
        DataStore<Order, String> orders = DataStores.observing(
                DataStores.inMemory(Order::id), Order::id, events::add);
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-9"))) {
            orders.save(Order.create("order-42", 9900L));
            orders.findById("order-42");
            orders.findById("no-such");
        }
        assertEquals(3, events.size());
        assertEquals("data.saved", events.get(0).name());
        assertEquals("alice", events.get(0).context().operator().subject());
        assertEquals("trace-9", events.get(0).context().traceId());
        assertEquals("order-42", events.get(0).attributes().get("data.id"));
        assertEquals("data.read.miss", events.get(2).name());
    }

    @Test
    void 内存兜底大声标注() {
        String text = DataStores.inMemory(Order::id).toString();
        assertTrue(text.contains("重启即失"), "边界自我声明：" + text);
        assertTrue(text.contains("adapter"));
    }
}
