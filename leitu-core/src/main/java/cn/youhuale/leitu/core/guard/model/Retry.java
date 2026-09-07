package cn.youhuale.leitu.core.guard.model;

import java.time.Duration;
import java.util.Objects;

/**
 * 否决后的重试语义：告诉调用方"怎么办"。
 *
 * <ul>
 *   <li>{@link Never}——永久否决（权限类：换个身份也没用，别重试）</li>
 *   <li>{@link Later}——稍后重试（限流/熔断类：过这段时间再来）</li>
 * </ul>
 */
public sealed interface Retry {

    record Never() implements Retry {
        @Override
        public String toString() {
            return "不重试";
        }
    }

    record Later(Duration after) implements Retry {
        public Later {
            Objects.requireNonNull(after, "after 必填：稍后重试必须给出等待时长（错误即教程）");
            if (after.isNegative() || after.isZero()) {
                throw new IllegalArgumentException("after 必须为正时长，当前为：" + after);
            }
        }

        @Override
        public String toString() {
            return after.toSeconds() + "s 后可重试";
        }
    }

    static Never never() {
        return new Never();
    }

    static Later later(Duration after) {
        return new Later(after);
    }
}
