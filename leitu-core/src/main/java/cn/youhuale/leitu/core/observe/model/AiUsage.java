package cn.youhuale.leitu.core.observe.model;

import java.util.Objects;

/**
 * AI 调用的用量事实：哪个模型、进出各多少 token。
 *
 * <p>token 是模型侧客观事实（业界对齐：OpenAI / Anthropic 的 usage 报数均以
 * input/output token 为准），可被测试守住。费用不走类型——币种、批价、缓存折减的
 * 变化原因在计费方，core 守不住；费用走事件 attributes（如 "ai.cost"）。
 */
public record AiUsage(String model, long inputTokens, long outputTokens) {

    public AiUsage {
        Objects.requireNonNull(model, "model 必填：不知道模型名就无法归因成本");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model 不能为空白：不知道模型名就无法归因成本");
        }
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token 数不能为负（未知记 0）：input=" + inputTokens
                    + "，output=" + outputTokens);
        }
    }

    public static AiUsage of(String model, long inputTokens, long outputTokens) {
        return new AiUsage(model, inputTokens, outputTokens);
    }

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    @Override
    public String toString() {
        return model + "（in=" + inputTokens + "，out=" + outputTokens + "）";
    }
}
