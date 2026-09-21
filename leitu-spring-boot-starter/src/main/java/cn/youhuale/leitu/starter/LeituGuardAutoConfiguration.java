package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.spi.Guard;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 判定链（allow-or-not）的默认装配：收编容器内全部 {@link Guard} Bean（@Order 排序），
 * 组成唯一的 {@link GuardChain}；未装配任何 Guard 时为空链——安全默认拒绝（deny-by-default）。
 *
 * <p>权限/预算 Guard 属策略面：应用声明 Bean（如 AccessGuards.roleMap(...)），此处自动收编——
 * 不做配置式自动生成（避免新造配置魔法，见 ADR-014）。
 */
@AutoConfiguration
public class LeituGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GuardChain guardChain(ObjectProvider<Guard> guards) {
        return GuardChain.of(guards.orderedStream().toArray(Guard[]::new));
    }
}
