package cn.youhuale.leitu.capability.access.internal;

import cn.youhuale.leitu.capability.access.model.BudgetSpec;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 预算 Guard 参考实现：窗口计数限次，耗尽即否决并携带重试语义。 */
public final class BudgetGuard implements Guard {

    private final String name;
    private final BudgetSpec spec;
    private final Clock clock;
    private final AtomicLong used = new AtomicLong();

    /** 当前窗口的结束时刻；到点即清零重新计数（固定窗口，够薄够用——BudgetSpec 的参考实现语义）。 */
    private Instant windowEnd;

    public BudgetGuard(String name, BudgetSpec spec) {
        this(name, spec, Clock.systemUTC());
    }

    /** 可注入时钟：测试拨一下时钟就能断言窗口重置，不必真等一个窗口。 */
    public BudgetGuard(String name, BudgetSpec spec, Clock clock) {
        this.name = Objects.requireNonNull(name, "name 必填：用于否决理由的可读性，如 \"ai-calls\"");
        this.spec = Objects.requireNonNull(spec, "spec 必填：用 BudgetSpec.of(...) 构造");
        this.clock = Objects.requireNonNull(clock, "clock 必填：窗口按时钟推进——测试传可拨 Clock");
    }

    /**
     * 窗口计数：跨过窗口结束时刻即清零重新计数。
     *
     * <p><b>计数必须真的能回零</b>：{@code Retry.later(...)} 向调用方承诺"过这段时间再来"，
     * 若计数只增不减，客户端照着重试语义再来，服务端却永久拒绝——承诺与实现不一致，
     * 且这种不一致只在流量打满后才暴露。
     *
     * <p>计数与窗口推进同处一把锁：限流要的是计数准确，不是高并发吞吐。
     */
    @Override
    public synchronized Decision check(AccessRequest request) {
        Instant now = clock.instant();
        if (windowEnd == null || !now.isBefore(windowEnd)) {
            windowEnd = now.plus(spec.refillAfter());
            used.set(0);
        }
        long n = used.incrementAndGet();
        if (n <= spec.limit()) {
            return Decision.allow();
        }
        // 重试语义仍按整个窗口时长给：窗口自首次调用起算，等满 refillAfter 必然已跨过窗口结束时刻
        return Decision.deny("预算[" + name + "]耗尽：限额 " + spec.limit()
                        + " 次/" + spec.refillAfter() + " 窗口，本次为第 " + n
                        + " 次；窗口于 " + windowEnd + " 重置",
                Retry.later(spec.refillAfter()));
    }

    @Override
    public synchronized String toString() {
        return "BudgetGuard[" + name + " " + used.get() + "/" + spec.limit() + "，窗口至 " + windowEnd + "]";
    }
}
