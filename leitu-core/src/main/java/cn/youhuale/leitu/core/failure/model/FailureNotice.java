package cn.youhuale.leitu.core.failure.model;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;

import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * 失败的交代结构——传输无关本体（HTTP problem+json 是 adapter 侧投影，见 ADR-011）。
 *
 * <p>组件对齐 RFC 9457 problem details：type/title/detail/扩展成员；另携 kind（分类）
 * 与 retry（复用判定链语义）。同一失败经 HTTP / 消息队列 / RPC / CLI 交代同构。
 */
public record FailureNotice(Kind kind, String type, String title, String detail,
                            Retry retry, String traceId, Map<String, String> attributes) {

    /** 系统错的固定安全值（fromThrowable / system 兜底共用）。 */
    public static final String SYSTEM_TYPE = "system.unexpected";
    public static final String SYSTEM_TITLE = "系统暂时无法完成请求";
    public static final String SYSTEM_DETAIL = "系统暂时无法完成请求，请稍后重试；可凭 traceId 联系支持查证";
    public static final String GUARD_TYPE = "guard.denied";

    /** type 会被投影为 URI（RFC 9457 的 type 成员），字符集因此必须 URI 安全。 */
    private static final Pattern URI_SAFE = Pattern.compile("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*");

    public FailureNotice {
        Objects.requireNonNull(kind,
                "kind 必填：先分类再交代——BUSINESS=调用方的错（全量交代），SYSTEM=我们的错（脱敏交代）");
        Objects.requireNonNull(type, "type 必填：机读标识（如 \"order.not-found\"）");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type 不能为空白：机读标识（如 \"order.not-found\"）");
        }
        if (type.indexOf('.') < 1) {
            throw new IllegalArgumentException("type 必须点分命名（如 \"order.not-found\"、\"system.unexpected\"）："
                    + "左侧是域、右侧是事实，交代可聚可查。当前：" + type);
        }
        // RFC 9457 的 type 是 URI：这里是它的末段，因此字符集必须在构造期就收口。
        // 空格、斜杠、冒号、问号一旦进来，web 侧拼出的 URI 会非法——而那时离写错的地方隔着整个调用链。
        if (!URI_SAFE.matcher(type).matches()) {
            throw new IllegalArgumentException("type 只能由字母数字与 . _ - 组成（且以字母数字开头结尾）：当前 \""
                    + type + "\"；它会被投影成 problem+json 的 type URI（RFC 9457），"
                    + "含空格 / 斜杠 / 冒号 / 问号一类字符会拼出非法 URI——"
                    + "正确示例：\"order.not-found\"、\"system.unexpected\"");
        }
        Objects.requireNonNull(title, "title 必填：给调用方看的稳定短句（错误即教程）");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title 不能为空白：给调用方看的稳定短句");
        }
        Objects.requireNonNull(detail, "detail 必填——交代必须说明情况（错误即教程）；系统错用 fromThrowable 的脱敏默认");
        if (detail.isBlank()) {
            throw new IllegalArgumentException("detail 不能为空白——交代必须说明情况（错误即教程）");
        }
        Objects.requireNonNull(retry, "retry 必填，不重试用 Retry.never()");
        Objects.requireNonNull(traceId, "traceId 必填：调用方凭它找支持；入口未绑定时传 ExecutionContextReader.current() 的匿名兜底");
        if (traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空白");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** 业务错：调用方的错，全量交代（默认不重试——改行为，别重试同样的错）。 */
    public static FailureNotice business(String type, String title, String detail, ExecutionContext ctx) {
        return business(type, title, detail, Retry.never(), ctx);
    }

    /** 业务错，携带重试语义（限流/配额类否决）。 */
    public static FailureNotice business(String type, String title, String detail, Retry retry, ExecutionContext ctx) {
        return new FailureNotice(Kind.BUSINESS, type, title, detail, retry,
                requireCtx(ctx).traceId(), Map.of());
    }

    /** 系统错：我们的错——detail 用通用脱敏默认，绝不接收调用方传入的细节。 */
    public static FailureNotice system(String type, String title, ExecutionContext ctx) {
        return system(type, title, Retry.never(), ctx);
    }

    /** 系统错，携带重试语义（如熔断窗口）。 */
    public static FailureNotice system(String type, String title, Retry retry, ExecutionContext ctx) {
        return new FailureNotice(Kind.SYSTEM, type, title, SYSTEM_DETAIL, retry,
                requireCtx(ctx).traceId(), Map.of());
    }

    /** 判定链否决 → 交代：reason→detail、retry 随行（Decision 对齐）。允许的 Decision 进来即装配错误，大声失败。 */
    public static FailureNotice fromDecision(Decision decision, ExecutionContext ctx) {
        Objects.requireNonNull(decision, "decision 必填：要交代的是哪次判定");
        if (decision.isAllow()) {
            throw new IllegalArgumentException(
                    "允许的 Decision 不是失败：交代失败请确认 verdict=deny，放行无需交代（装配错误大声失败）");
        }
        return new FailureNotice(Kind.BUSINESS, GUARD_TYPE, "请求被拒绝",
                decision.reason(), decision.retry(), requireCtx(ctx).traceId(), Map.of());
    }

    /**
     * 异常 → 系统错交代（脱敏默认）：异常消息与类名不进入交代，只带 traceId。
     * 全量细节请走观测通道（ObservationEvent + Outcome.failure），不走交代通道。
     */
    public static FailureNotice fromThrowable(Throwable t, ExecutionContext ctx) {
        Objects.requireNonNull(t, "t 必填：要交代的是哪个异常；非异常失败用 system(...)");
        return new FailureNotice(Kind.SYSTEM, SYSTEM_TYPE, SYSTEM_TITLE,
                SYSTEM_DETAIL, Retry.never(), requireCtx(ctx).traceId(), Map.of());
    }

    private static ExecutionContext requireCtx(ExecutionContext ctx) {
        return Objects.requireNonNull(ctx,
                "ctx 必填：交代要带 traceId；入口未绑定时传 ExecutionContextReader.current() 的匿名兜底");
    }

    public boolean isBusiness() {
        return kind == Kind.BUSINESS;
    }

    public boolean isSystem() {
        return kind == Kind.SYSTEM;
    }

    /** 单行可 grep：type｜kind｜title｜detail｜retry｜trace=…｜attributes（非空才带）。 */
    @Override
    public String toString() {
        StringJoiner j = new StringJoiner("｜");
        j.add(type);
        j.add(kind.name());
        j.add(title);
        j.add(detail);
        j.add(retry.toString());
        j.add("trace=" + traceId);
        if (!attributes.isEmpty()) {
            j.add(attributes.toString());
        }
        return j.toString();
    }
}
