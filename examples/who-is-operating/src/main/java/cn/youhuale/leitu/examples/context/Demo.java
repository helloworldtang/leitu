package cn.youhuale.leitu.examples.context;

import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;

import java.util.Set;

/**
 * 金样本：执行上下文的最小可运行示范——入口绑定、业务只读、作用域还原、匿名兜底，
 * 以及与判定链的标准配合（who-is-operating 是 allow-or-not 的依赖根）。
 */
public final class Demo {

    public static void main(String[] args) {
        var binder = ExecutionContextBinders.threadLocal();
        ExecutionContextReader who = ExecutionContextReader.threadLocal();

        // 1) 入口未绑定 → 匿名兜底（不抛异常，观测有 traceId）
        System.out.println("未绑定: " + who.current());

        // 2) 入口绑定（宿主/过滤器干的事），业务只读，退出自动还原
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-100"))) {
            System.out.println("绑定后: " + who.current());

            // 3) 嵌套：调用下游前换成 agent 身份，内层退出恢复外层
            try (var inner = binder.bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "trace-100"))) {
                System.out.println("嵌套:   " + who.current());
            }
            System.out.println("内层退: " + who.current());
        }
        System.out.println("外层退: " + who.current());

        // 4) 与判定链配合：匿名操作者过权限 Guard → 否决
        GuardChain chain = GuardChain.of(req -> Set.of("alice").contains(req.subject())
                ? Decision.allow()
                : Decision.deny("仅名单内操作者可执行 " + req.action() + "，当前为 " + req.subject()));
        Decision d = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "order:cancel-own", "order-42"));
        System.out.println("判定:   " + d);
    }
}
