package com.example.fxservice;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/** 装配：汇率读自配置源（读时求值）+ 守卫声明即收编。 */
@Configuration
public class FxConfig {

    private static final Set<String> ALLOWED = Set.of("alice", "bob");

    /**
     * 汇率查询面：读时求值——每次调用都从配置源取当前值，改配置不用重启。
     * 键约定 {@code fx.rate.<FROM>-<TO>}；缺失返回 Optional.empty()，由调用方决定怎么交代。
     */
    @Bean
    FxRates fxRates(ConfigReader config) {
        return (from, to) -> config.get("fx.rate." + from + "-" + to);
    }

    /** 演示守卫：fx.convert 仅 alice / bob 放行。 */
    @Bean
    Guard fxConvertGuard() {
        return request -> "fx.convert".equals(request.action()) && !ALLOWED.contains(request.subject())
                ? Decision.deny("仅 alice / bob 可换算（探针守卫）", Retry.never())
                : Decision.allow();
    }

    /** 汇率查询面（函数接口，测试可换实现）。 */
    @FunctionalInterface
    public interface FxRates {
        java.util.Optional<String> rate(String from, String to);
    }
}
