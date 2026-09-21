package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 观测（what-happened）的默认装配：收编容器内全部 {@link ObservationSink} Bean，一次记录扇出全部落点；
 * 无落点时落日志级默认（观测维度=最小可用实现，非 Noop——分维度默认值）。
 */
@AutoConfiguration
public class LeituObserveAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObservationRecorder observationRecorder(ObjectProvider<ObservationSink> sinks) {
        return ObservationRecorder.of(sinks.orderedStream().toArray(ObservationSink[]::new));
    }
}
