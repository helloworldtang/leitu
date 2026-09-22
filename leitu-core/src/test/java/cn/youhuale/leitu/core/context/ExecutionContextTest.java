package cn.youhuale.leitu.core.context;

import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.internal.ThreadLocalBinder;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextTest {

    private final ThreadLocalBinder binder = ThreadLocalBinder.shared();

    @Test
    void 未绑定读到匿名兜底_永不返回null() {
        ExecutionContextReader reader = ExecutionContextReader.threadLocal();
        ExecutionContext ctx = reader.current();
        assertNotNull(ctx);
        assertTrue(ctx.operator().isAnonymous());
        assertFalse(ctx.traceId().isBlank(), "匿名兜底也要有 traceId（观测锚点不缺席）");
    }

    @Test
    void 绑定后可读_退出作用域自动还原() {
        try (ExecutionContextBinder.Scope s =
                     binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-1"))) {
            assertEquals("alice", binder.current().operator().subject());
            assertEquals("trace-1", binder.current().traceId());
        }
        assertTrue(binder.current().operator().isAnonymous(), "作用域退出后必须还原，不泄漏");
    }

    @Test
    void 嵌套绑定恢复前值() {
        try (ExecutionContextBinder.Scope s1 =
                     binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t1"))) {
            try (ExecutionContextBinder.Scope s2 =
                         binder.bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "t2"))) {
                assertEquals(Operator.Kind.AGENT, binder.current().operator().kind());
            }
            assertEquals("alice", binder.current().operator().subject(), "内层退出恢复外层");
        }
    }

    @Test
    void 显式traceId保留_自动生成非空() {
        String generated = ExecutionContext.of(Operator.system()).traceId();
        assertFalse(generated.isBlank());
        assertEquals("t-fixed", ExecutionContext.of(Operator.system(), "t-fixed").traceId());
    }

    @Test
    void 操作者类型三态齐备() {
        assertEquals(Operator.Kind.SYSTEM, Operator.system().kind());
        assertEquals(Operator.Kind.AGENT, Operator.agent("bot", "tenant-a").kind());
        assertEquals(Operator.Kind.HUMAN, Operator.human("u1", "tenant-a").kind());
        assertEquals("-", Operator.system().tenant(), "无租户语义用 -");
    }

    /**
     * 反向自测（#15）：匿名是"未识别的调用方"这种类型，不是 HUMAN 的一个取值。
     * 标成 HUMAN 时它能走进角色判定——只要某处给 "anonymous" 配了角色，
     * 未认证流量就拿到人的权限，而配置表面看不出异常。
     */
    @Test
    void 匿名是独立类型_不是人的一种取值() {
        assertEquals(Operator.Kind.ANONYMOUS, Operator.anonymous().kind());
        assertTrue(Operator.anonymous().isAnonymous());
        assertFalse(Operator.human("u1", "tenant-a").isAnonymous());
    }

    /** 按类型判，不按名字猜：真有个用户叫 anonymous，他也是人。 */
    @Test
    void 名叫anonymous的真人不算匿名() {
        Operator person = Operator.human("anonymous", "tenant-a");
        assertEquals(Operator.Kind.HUMAN, person.kind());
        assertFalse(person.isAnonymous(), "名字撞车不该把真人判成未认证");
    }

    @Test
    void reader可由任意binder构造_测试可注入fake() {
        ExecutionContext fixed = ExecutionContext.of(Operator.system(), "t");
        ExecutionContextReader fake = ExecutionContextReader.of(new ExecutionContextBinder() {
            @Override
            public ExecutionContext current() {
                return fixed;
            }

            @Override
            public Scope bind(ExecutionContext context) {
                throw new UnsupportedOperationException("fake 仅用于读");
            }
        });
        assertEquals("system", fake.current().operator().subject());
    }
}
