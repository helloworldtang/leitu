package cn.youhuale.leitu.core.context.api;

import cn.youhuale.leitu.core.context.internal.ThreadLocalBinder;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;

/**
 * 端口：读当前执行上下文——业务代码认识"谁在操作"的全部接口。
 *
 * <p>注入使用；未绑定时读到匿名兜底（subject=anonymous）。绑定是宿主/入口的事
 * （{@link ExecutionContextBinder}），业务只读不写——最小权限。
 *
 * <p>用法：
 * <pre>{@code
 * ExecutionContextReader who;  // 注入
 * Operator op = who.current().operator();
 * String traceId = who.current().traceId();
 * }</pre>
 */
public interface ExecutionContextReader {

    /** 当前执行上下文，永不返回 null。 */
    ExecutionContext current();

    /** 由任意绑定机制构造只读端口。 */
    static ExecutionContextReader of(ExecutionContextBinder binder) {
        return binder::current;
    }

    /** 默认实现：线程级绑定（业务可注入；测试可换任意 fake binder）。 */
    static ExecutionContextReader threadLocal() {
        return ThreadLocalBinder.shared()::current;
    }
}
