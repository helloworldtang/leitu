package cn.youhuale.leitu.core.context.spi;

import cn.youhuale.leitu.core.context.model.ExecutionContext;

/**
 * 上下文的绑定机制——宿主/入口适配器实现（HTTP 过滤器、消息监听器、任务入口……）。
 *
 * <p>约定：入口处 {@link #bind}，出口由返回的 {@link Scope} 自动还原（try-with-resources）；
 * 未绑定时 {@link #current()} 必须返回匿名兜底，不得抛异常。
 */
public interface ExecutionContextBinder {

    /** 当前上下文；未绑定时返回 {@link ExecutionContext#anonymous()}。 */
    ExecutionContext current();

    /** 绑定并返回还原作用域：try-with-resources 退出时恢复 previous（无则清除）。 */
    Scope bind(ExecutionContext context);

    /** 绑定作用域。close() 不抛检查异常，可直接用于 try-with-resources。 */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
