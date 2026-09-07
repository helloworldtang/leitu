package cn.youhuale.leitu.capability.access.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 预算规格：窗口内允许 {@code limit} 次，耗尽后等待 {@code refillAfter} 再试。
 *
 * <p>参考实现语义（简单计数窗口）：够薄、够用；令牌桶/滑动窗口等更强语义沿
 * PermissionPolicy/自定义 Guard 扩展缝接入。
 */
public record BudgetSpec(long limit, Duration refillAfter) {

    public BudgetSpec {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须为正数，当前为：" + limit);
        }
        Objects.requireNonNull(refillAfter, "refillAfter 必填：耗尽后必须告知何时可重试（错误即教程）");
        if (refillAfter.isNegative() || refillAfter.isZero()) {
            throw new IllegalArgumentException("refillAfter 必须为正时长，当前为：" + refillAfter);
        }
    }

    public static BudgetSpec of(long limit, Duration refillAfter) {
        return new BudgetSpec(limit, refillAfter);
    }
}
