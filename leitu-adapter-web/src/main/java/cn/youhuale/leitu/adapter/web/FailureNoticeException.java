package cn.youhuale.leitu.adapter.web;

import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.model.Decision;

import java.util.Objects;

/**
 * 失败交代的载体异常：业务代码"抛出失败"的标准姿势——
 * 携带 {@link FailureNotice}，由 web 投影统一交代为 problem+json。
 *
 * <p>判定链否决最常用的姿势见 {@link #from(Decision, ExecutionContextReader)}。
 */
public class FailureNoticeException extends RuntimeException {

    private final FailureNotice notice;

    public FailureNoticeException(FailureNotice notice) {
        super(Objects.requireNonNull(notice, "notice 必填：要交代的失败").title());
        this.notice = notice;
    }

    public FailureNotice notice() {
        return notice;
    }

    /** 判定链否决 → 失败异常（reason→detail、retry 随行；Decision 对齐）。 */
    public static FailureNoticeException from(Decision decision, ExecutionContextReader who) {
        return new FailureNoticeException(FailureNotice.fromDecision(decision, who.current()));
    }
}
