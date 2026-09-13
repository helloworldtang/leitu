package cn.youhuale.leitu.core.failure.model;

/** 失败的两分：谁的错，决定交代的尺度。 */
public enum Kind {
    /** 调用方的错：参数 / 权限 / 状态不允许 / 判定链否决——改行为可解决，可全量交代。 */
    BUSINESS,
    /** 我们的错：含下游与三方故障（从调用方视角都是我们的错）——默认脱敏交代。 */
    SYSTEM
}
