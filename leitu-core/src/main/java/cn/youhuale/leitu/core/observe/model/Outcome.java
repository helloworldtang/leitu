package cn.youhuale.leitu.core.observe.model;

import java.util.Objects;

/**
 * 结果语义：操作完成类事件的成功/失败。
 *
 * <p>失败必须说明原因（错误即教程）——构造期强制，不留给运行期猜。
 * 纯事实事件（如 cache.hit）不携带 outcome。
 */
public sealed interface Outcome {

    record Success() implements Outcome {
        @Override
        public String toString() {
            return "成功";
        }
    }

    record Failure(String reason) implements Outcome {
        public Failure {
            Objects.requireNonNull(reason, "reason 必填——失败必须说明原因（错误即教程）");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason 不能为空白——失败必须说明原因（错误即教程）");
            }
        }

        @Override
        public String toString() {
            return "失败（" + reason + "）";
        }
    }

    static Outcome success() {
        return new Success();
    }

    static Outcome failure(String reason) {
        return new Failure(reason);
    }
}
