package cn.youhuale.leitu.examples.context;

import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定执行上下文的标准用法语义。core 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    private final ExecutionContextBinder binder = ExecutionContextBinders.threadLocal();
    private final ExecutionContextReader who = ExecutionContextReader.threadLocal();

    @Test
    void 入口绑定_业务只读_退出还原() {
        try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertEquals("alice", who.current().operator().subject());
            assertEquals("t-1", who.current().traceId());
        }
        assertTrue(who.current().operator().isAnonymous());
    }

    @Test
    void 匿名过权限Guard被否决_上下文与判定链的标准配合() {
        GuardChain chain = GuardChain.of(req -> Set.of("alice").contains(req.subject())
                ? Decision.allow()
                : Decision.deny("仅名单内操作者可执行 " + req.action()));
        Decision d = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "order:cancel-own", "order-42"));
        assertTrue(d.isDeny(), "匿名操作者必须被权限 Guard 拦下（deny-by-default 的纵深）");
    }

    @Test
    void agent身份可区分_AI操作者是一等公民() {
        try (var scope = binder.bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "t-2"))) {
            assertEquals(Operator.Kind.AGENT, who.current().operator().kind());
        }
    }
}
