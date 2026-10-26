package com.example.fxservice;

import cn.youhuale.leitu.adapter.web.FailureNoticeException;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/fx")
public class FxController {

    private final FxConfig.FxRates rates;
    private final GuardChain chain;
    private final ExecutionContextReader who;
    private final ObservationRecorder recorder;

    public FxController(FxConfig.FxRates rates, GuardChain chain, ExecutionContextReader who,
                        ObservationRecorder recorder) {
        this.rates = rates;
        this.chain = chain;
        this.who = who;
        this.recorder = recorder;
    }

    @Operation(operationId = "fx.convert", summary = "汇率换算")
    @GetMapping("/convert")
    public Map<String, Object> convert(@RequestParam String from, @RequestParam String to,
                                       @RequestParam double amount, HttpServletResponse response) {
        long start = System.nanoTime();
        // 成功响应的 traceId 由端点自带（adapter 只在 problem+json 投影里统一加）：
        // 事件与响应头同源，都取执行上下文。
        response.setHeader("X-Trace-Id", who.current().traceId());
        Decision decision = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "fx.convert", "-"));
        if (decision.isDeny()) {
            record("fx.denied", from, to, amount, Outcome.failure(decision.reason()), start);
            throw FailureNoticeException.from(decision, who);
        }
        Optional<String> rate = rates.rate(from, to);
        if (rate.isEmpty()) {
            record("fx.pair-not-found", from, to, amount, Outcome.failure("未配置汇率"), start);
            throw new FailureNoticeException(FailureNotice.business("fx.pair-not-found", "币对未配置",
                    "未配置 " + from + " → " + to + " 的汇率；请联系服务方在配置中补充", who.current()));
        }
        double result = amount * Double.parseDouble(rate.get());
        record("fx.converted", from, to, amount, Outcome.success(), start);
        return Map.of("from", from, "to", to, "amount", amount,
                "rate", Double.parseDouble(rate.get()), "result", result);
    }

    private void record(String name, String from, String to, double amount, Outcome outcome, long startNanos) {
        recorder.record(new ObservationEvent(name, who.current(), Instant.now(), outcome,
                Duration.ofNanos(System.nanoTime() - startNanos), null,
                Map.of("pair", from + "/" + to, "amount", String.valueOf(amount))));
    }
}
