package cn.youhuale.leitu.examples.data;

import cn.youhuale.leitu.capability.access.api.AccessGuards;
import cn.youhuale.leitu.capability.access.model.RoleMap;
import cn.youhuale.leitu.capability.data.api.DataStores;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 金样本：data 能力的最小可运行示范。
 *
 * <p>照着这个样子读写业务数据——这是"业务数据怎么读写"的标准答案用法
 * （docs/problems/data-access.md；消费 who-is-operating / allow-or-not / what-happened 三条答案）。
 */
public final class Demo {

    public static void main(String[] args) {
        var binder = ExecutionContextBinders.threadLocal();
        ExecutionContextReader who = ExecutionContextReader.threadLocal();
        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = events::add;
        DataStore<Order, String> orders = DataStores.observing(
                DataStores.inMemory(Order::id), Order::id, recorder);

        // 1) 审计四件套自动盖章：alice 建、bob 改——createdBy 不变，updatedBy 换新
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            orders.save(Order.create("order-42", 9900L));
        }
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("bob", "tenant-a"), "t-2"))) {
            orders.save(Order.create("order-42", 12800L));
        }
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-3"))) {
            AuditFields a = orders.findById("order-42").orElseThrow().auditFields();
            System.out.println("审计:     createdBy=" + a.createdBy() + " createdAt=" + a.createdAt()
                    + " updatedBy=" + a.updatedBy() + " updatedAt=" + a.updatedAt());
        }

        // 2) 租户隔离：tenant-b 对同主键三操作全扑空（不可见、不泄漏、不报错）
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("mallory", "tenant-b"), "t-4"))) {
            System.out.println("隔离:     tenant-b findById=" + orders.findById("order-42")
                    + " findAll=" + orders.findAll().size() + " delete=" + orders.deleteById("order-42"));
        }

        // 3) "-" 系统作用域：系统行只对 "-" 上下文可见，不是全局通配
        try (var scope = binder.bind(ExecutionContext.of(Operator.system(), "t-sys"))) {
            orders.save(Order.create("order-sys", 1L));
        }
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-5"))) {
            System.out.println("系统作用域: tenant-a 看系统行=" + orders.findById("order-sys").isPresent());
        }

        // 4) 数据权限走判定链：bob 无 order:write → deny → 不落库；alice(admin) → 放行
        RoleMap roles = RoleMap.create()
                .subject("alice").hasRoles("admin")
                .subject("bob").hasRoles("member")
                .action("order:write").allowsRoles("admin")
                .build();
        GuardChain guard = GuardChain.of(AccessGuards.roleMap(roles));
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("bob", "tenant-a"), "t-6"))) {
            Decision d = guard.check(AccessRequest.inbound(who.current().operator().subject(),
                    "order:write", "order-77"));
            System.out.println("判定:     bob 写 order-77 → " + d);
            if (d.isAllow()) {
                orders.save(Order.create("order-77", 100L));
            }
        }
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-7"))) {
            Decision d = guard.check(AccessRequest.inbound(who.current().operator().subject(),
                    "order:write", "order-77"));
            System.out.println("判定:     alice 写 order-77 → " + d);
            if (d.isAllow()) {
                orders.save(Order.create("order-77", 100L));
            }
        }

        // 5) 观察装饰：data.* 事件带完整锚点（谁 + traceId）
        events.forEach(e -> System.out.println("观测:     " + e));

        // 6) 内存兜底大声标注
        System.out.println("兜底:     " + DataStores.inMemory(Order::id));
    }
}
