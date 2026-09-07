package cn.youhuale.leitu.capability.access.internal;

import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 预算 Guard 参考实现：窗口计数限次，耗尽即否决并携带重试语义。 */
public final class BudgetGuard implements Guard {

    private final String name;
    private final BudgetSpec spec;
    private final AtomicLong used = new AtomicLong();

    public BudgetGuard(String name, BudgetSpec spec) {
        this.name = Objects.requireNonNull(name, "name 必填：用于否决理由的可读性，如 \"ai-calls\"");
        this.spec = Objects.requireNonNull(spec, "spec 必填：用 BudgetSpec.of(...) 构造");
    }

    @Override
    public Decision check(AccessRequest request) {
        long n = used.incrementAndGet();
        if (n <= spec.limit()) {
            return Decision.allow();
        }
        return Decision.deny("预算[" + name + "]耗尽：限额 " + spec.limit()
                        + " 次/" + spec.refillAfter() + " 窗口，本次为第 " + n + " 次",
                Retry.later(spec.refillAfter()));
    }

    @Override
    public String toString() {
        return "BudgetGuard[" + name + " " + used.get() + "/" + spec.limit() + "]";
    }
}
