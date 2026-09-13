package cn.youhuale.leitu.core.failure;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.failure.model.Kind;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 失败交代的单测：锁分类教义、脱敏默认与判定链对齐。 */
class FailureNoticeTest {

    private final ExecutionContext ctx = ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1");

    @Test
    void type必须点分命名_构造即校验并教修复方式() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FailureNotice.business("ordernotfound", "标题", "详情", ctx));
        assertTrue(e.getMessage().contains("点分"), "异常必须即教程：" + e.getMessage());
        assertTrue(e.getMessage().contains("当前：ordernotfound"), "必须报出当前值：" + e.getMessage());
    }

    @Test
    void title与detail必填_空白也不行_null即教程异常() {
        NullPointerException npe = assertThrows(NullPointerException.class,
                () -> FailureNotice.business("order.not-found", null, "详情", ctx));
        assertTrue(npe.getMessage().contains("必填"));
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
                () -> FailureNotice.business("order.not-found", "  ", "详情", ctx));
        assertTrue(iae.getMessage().contains("不能为空白"));
        IllegalArgumentException iae2 = assertThrows(IllegalArgumentException.class,
                () -> FailureNotice.business("order.not-found", "标题", "  ", ctx));
        assertTrue(iae2.getMessage().contains("错误即教程"));
    }

    @Test
    void retry必填_null即教程异常() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new FailureNotice(Kind.BUSINESS, "order.not-found", "标题", "详情",
                        null, "t-1", Map.of()));
        assertTrue(e.getMessage().contains("Retry.never()"), "必须给出修复方式：" + e.getMessage());
    }

    @Test
    void traceId必填_null教匿名兜底出路() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new FailureNotice(Kind.BUSINESS, "order.not-found", "标题", "详情",
                        Retry.never(), null, Map.of()));
        assertTrue(e.getMessage().contains("匿名兜底"), "必须给出出路：" + e.getMessage());
    }

    @Test
    void kind必填_null即教程异常() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> new FailureNotice(null, "order.not-found", "标题", "详情",
                        Retry.never(), "t-1", Map.of()));
        assertTrue(e.getMessage().contains("BUSINESS"), "必须教分类：" + e.getMessage());
        assertTrue(e.getMessage().contains("SYSTEM"));
    }

    @Test
    void attributes防御拷贝_null转空Map() {
        Map<String, String> src = new HashMap<>();
        src.put("k", "v");
        FailureNotice notice = new FailureNotice(Kind.BUSINESS, "order.not-found", "标题", "详情",
                Retry.never(), "t-1", src);
        src.put("k2", "漂移");
        assertEquals(Map.of("k", "v"), notice.attributes());

        FailureNotice nullAttrs = new FailureNotice(Kind.BUSINESS, "order.not-found", "标题", "详情",
                Retry.never(), "t-1", null);
        assertTrue(nullAttrs.attributes().isEmpty());
    }

    @Test
    void business工厂_默认不重试_全量交代() {
        FailureNotice notice = FailureNotice.business("order.not-found", "订单不存在",
                "订单 ord-9527 不存在或已删除；请确认订单号后重试", ctx);
        assertTrue(notice.isBusiness());
        assertInstanceOf(Retry.Never.class, notice.retry(), "业务错默认不重试——改行为，别重试同样的错");
        assertTrue(notice.detail().contains("ord-9527"), "业务错全量交代");
        assertEquals("t-1", notice.traceId());
    }

    @Test
    void business工厂_可携带重试语义() {
        FailureNotice notice = FailureNotice.business("guard.denied", "请求被拒绝", "太频繁",
                Retry.later(Duration.ofSeconds(30)), ctx);
        Retry.Later later = assertInstanceOf(Retry.Later.class, notice.retry());
        assertEquals(Duration.ofSeconds(30), later.after());
    }

    @Test
    void system工厂_detail用通用脱敏默认() {
        FailureNotice notice = FailureNotice.system("order.query", "查询失败", ctx);
        assertTrue(notice.isSystem());
        assertEquals(FailureNotice.SYSTEM_DETAIL, notice.detail(), "system 不收 detail——脱敏教义在工厂面成立");
        assertEquals("order.query", notice.type(), "type 是自定义入参；SYSTEM_TYPE 常量只在 fromThrowable 用");
    }

    @Test
    void fromThrowable_异常消息与类名不出现在交代里() {
        Throwable boom = new IllegalStateException(
                "jdbc:mysql://prod-db:3306/order?user=root&password=s3cret 查询超时");
        FailureNotice notice = FailureNotice.fromThrowable(boom, ctx);
        assertTrue(notice.isSystem());
        assertEquals("system.unexpected", notice.type());
        assertEquals("t-1", notice.traceId(), "traceId 在场——调用方凭它找支持");
        String text = notice.toString();
        assertFalse(text.contains("jdbc"), "异常消息不出端：" + text);
        assertFalse(text.contains("s3cret"), "凭据绝不泄漏：" + text);
        assertFalse(text.contains("IllegalState"), "类名也不出端：" + text);
    }

    @Test
    void fromDecision_保留理由与重试语义() {
        Decision deny = Decision.deny("预算耗尽，稍后重试", Retry.later(Duration.ofSeconds(30)));
        FailureNotice notice = FailureNotice.fromDecision(deny, ctx);
        assertTrue(notice.isBusiness(), "判定否决是调用方的错");
        assertEquals("guard.denied", notice.type());
        assertTrue(notice.detail().contains("预算耗尽"), "理由进交代");
        Retry.Later later = assertInstanceOf(Retry.Later.class, notice.retry());
        assertEquals(Duration.ofSeconds(30), later.after(), "重试语义随行（Decision 对齐）");
    }

    @Test
    void fromDecision_允许的判定大声失败_消息教修复() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FailureNotice.fromDecision(Decision.allow(), ctx));
        assertTrue(e.getMessage().contains("不是失败"), "装配错误大声失败：" + e.getMessage());
    }

    @Test
    void toString单行可grep_含type与traceId() {
        FailureNotice notice = FailureNotice.business("order.not-found", "订单不存在",
                "订单 ord-9527 不存在", ctx);
        String text = notice.toString();
        assertTrue(text.contains("order.not-found"));
        assertTrue(text.contains("BUSINESS"));
        assertTrue(text.contains("trace=t-1"));
    }

    @Test
    void 上下文必填_null即教程异常() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> FailureNotice.business("order.not-found", "标题", "详情", null));
        assertTrue(e.getMessage().contains("匿名兜底"), "必须给出出路：" + e.getMessage());
    }
}
