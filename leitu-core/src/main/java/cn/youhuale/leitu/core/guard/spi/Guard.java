package cn.youhuale.leitu.core.guard.spi;

import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;

/**
 * 判定链上的一类否决源：功能权限 / 数据权限 / 限流 / 配额 / 熔断 / 维护窗口 / 风控……
 *
 * <p>各自独立演化，共享同一协议（见 docs/problems/allow-or-not.md）。实现保持无状态或线程安全；
 * 判定不允许抛异常中断链——异常本身应表达为否决 Decision。
 */
@FunctionalInterface
public interface Guard {

    /**
     * 对一次访问/调用给出判定。
     *
     * @return 永不返回 null——null 视为链装配错误，链执行将立即失败并指明修复方式
     */
    Decision check(AccessRequest request);
}
