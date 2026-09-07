package cn.youhuale.leitu.core.context.api;

import cn.youhuale.leitu.core.context.internal.ThreadLocalBinder;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;

/**
 * 绑定机制的工厂——宿主/入口从 api 取默认实现，不触碰 internal。
 *
 * <p>用法（入口处，try-with-resources）：
 * <pre>{@code
 * ExecutionContextBinder binder = ExecutionContextBinders.threadLocal();
 * try (var scope = binder.bind(ExecutionContext.of(Operator.human("alice", "tenant-a")))) {
 *     // 业务执行期，ExecutionContextReader 处处可读
 * }
 * }</pre>
 */
public final class ExecutionContextBinders {

    private ExecutionContextBinders() {
    }

    /** 线程级默认绑定（与 {@code ExecutionContextReader.threadLocal()} 同源）。 */
    public static ExecutionContextBinder threadLocal() {
        return ThreadLocalBinder.shared();
    }
}
