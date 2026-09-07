package cn.youhuale.leitu.core.context.internal;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;

import java.util.Objects;

/** 线程级默认绑定。外部经 {@code ExecutionContextBinder} 的实现约定使用，不直接实例化。 */
public final class ThreadLocalBinder implements ExecutionContextBinder {

    private static final ThreadLocalBinder SHARED = new ThreadLocalBinder();

    public static ThreadLocalBinder shared() {
        return SHARED;
    }

    private final ThreadLocal<ExecutionContext> holder = new ThreadLocal<>();

    private ThreadLocalBinder() {
    }

    @Override
    public ExecutionContext current() {
        ExecutionContext context = holder.get();
        return context != null ? context : ExecutionContext.anonymous();
    }

    @Override
    public Scope bind(ExecutionContext context) {
        Objects.requireNonNull(context, "context 必填：绑定匿名请显式用 ExecutionContext.anonymous()");
        ExecutionContext previous = holder.get();
        holder.set(context);
        return () -> {
            if (previous != null) {
                holder.set(previous);
            } else {
                holder.remove();
            }
        };
    }
}
