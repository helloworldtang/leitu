package cn.youhuale.leitu.core.observe.model;

import cn.youhuale.leitu.core.context.model.ExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 发生了什么的一次记录——统一事件协议。
 *
 * <p>横切字段全部强类型：name（点分命名，检索键）、context（谁 + traceId，锚点复用
 * who-is-operating）、occurredAt、outcome（成功/失败必带原因）、duration、aiUsage
 * （AI 用量事实）、attributes（领域自有细节的逃生门，不可变拷贝）。
 * 可选组件为 null 表示"无此语义"，不强加默认值。
 */
public record ObservationEvent(String name, ExecutionContext context, Instant occurredAt,
                               Outcome outcome, Duration duration, AiUsage aiUsage,
                               Map<String, String> attributes) {

    public ObservationEvent {
        Objects.requireNonNull(name, "name 必填：事件名是检索键（如 \"cache.hit\"）");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空白：事件名是检索键（如 \"cache.hit\"）");
        }
        if (name.indexOf('.') < 1) {
            throw new IllegalArgumentException("name 必须点分命名（如 \"cache.hit\"、\"ai.invocation\"）："
                    + "左侧是域、右侧是事实，事件可聚可查。当前：" + name);
        }
        Objects.requireNonNull(context, "context 必填：观测锚点（谁 + traceId）不缺席；"
                + "入口未绑定时传 ExecutionContextReader.current() 的匿名兜底");
        Objects.requireNonNull(occurredAt, "occurredAt 必填：不知道精确时刻就用 Instant.now()");
        if (duration != null && duration.isNegative()) {
            throw new IllegalArgumentException("duration 不能为负：" + duration);
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** 纯事实事件（occurredAt=now，无结果语义）。 */
    public static ObservationEvent of(String name, ExecutionContext context) {
        return new ObservationEvent(name, context, Instant.now(), null, null, null, null);
    }

    /** 结果事件（无时长语义）。 */
    public static ObservationEvent of(String name, ExecutionContext context, Outcome outcome) {
        return new ObservationEvent(name, context, Instant.now(), outcome, null, null, null);
    }

    /** 操作完成事件：结果 + 耗时。 */
    public static ObservationEvent of(String name, ExecutionContext context, Outcome outcome, Duration duration) {
        return new ObservationEvent(name, context, Instant.now(), outcome, duration, null, null);
    }

    /** AI 调用成功事件（name="ai.invocation"）；费用放 attributes（如 "ai.cost"）。失败调用无用量，用 {@link #of(String, ExecutionContext, Outcome)}。 */
    public static ObservationEvent aiCall(ExecutionContext context, AiUsage usage,
                                          Duration duration, Map<String, String> attributes) {
        return new ObservationEvent("ai.invocation", context, Instant.now(),
                Outcome.success(), duration, usage, attributes);
    }

    /** 单行可 grep：name｜trace=…｜who=…｜结果｜耗时｜AI 用量｜attributes。null 组件跳过。 */
    @Override
    public String toString() {
        StringJoiner j = new StringJoiner("｜");
        j.add(name);
        j.add("trace=" + context.traceId());
        j.add("who=" + context.operator().subject() + "/" + context.operator().kind()
                + "@" + context.operator().tenant());
        if (outcome != null) {
            j.add(outcome.toString());
        }
        if (duration != null) {
            j.add(duration.toMillis() + "ms");
        }
        if (aiUsage != null) {
            j.add(aiUsage.toString());
        }
        if (!attributes.isEmpty()) {
            j.add(attributes.toString());
        }
        return j.toString();
    }
}
