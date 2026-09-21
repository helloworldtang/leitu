package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.core.config.api.ConfigReader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 缓存（cache）的策略装配：从配置读 {@link CachePolicy}
 * （cache.max-size / cache.ttl / cache.enabled，缺省 1000 / PT30M / true）。
 *
 * <p>具体缓存的装配由应用在装配处显式分支（enabled ? inMemory : noop，决策可见）——
 * 缓存实例按用途命名，不做全局兜底自动装配（泛型键值，见 ADR-014）。
 */
@AutoConfiguration
public class LeituCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CachePolicy cachePolicy(ConfigReader config) {
        return CachePolicy.fromConfig(config);
    }
}
