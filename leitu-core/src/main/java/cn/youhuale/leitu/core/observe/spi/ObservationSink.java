package cn.youhuale.leitu.core.observe.spi;

import cn.youhuale.leitu.core.observe.model.ObservationEvent;

/**
 * 观测事件的落点——adapter/宿主实现（接日志管线、OTel、Micrometer、审计库……）。
 *
 * <p>三支柱是投影：logs / metrics / traces 由不同落点从同一事件流各自投影，
 * core 只承诺事件协议（见 docs/problems/what-happened.md）。
 *
 * <p>约定：accept 不抛异常——观测失败不许打断业务主流程；确实失败请自行记错
 * （大声但不致命）。
 */
@FunctionalInterface
public interface ObservationSink {

    /** 接收一个事件。 */
    void accept(ObservationEvent event);
}
