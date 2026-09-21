package cn.youhuale.leitu.examples.springboot;

import cn.youhuale.leitu.adapter.web.FailureNoticeException;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 金样本控制器：判定 → 读写 → 失败交代，全部走标准答案。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final DataStore<Order, String> orders;
    private final GuardChain chain;
    private final ExecutionContextReader who;

    public OrderController(DataStore<Order, String> orders, GuardChain chain, ExecutionContextReader who) {
        this.orders = orders;
        this.chain = chain;
        this.who = who;
    }

    /** operationId 与判定链 action 点分同源（order.create）——建议做法，见 ADR-013 增值点 3。 */
    @Operation(operationId = "order.create", summary = "创建订单")
    @PostMapping
    public Order create(@RequestParam String id, @RequestParam long amountCents) {
        Decision decision = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "order.create", "-"));
        if (decision.isDeny()) {
            throw FailureNoticeException.from(decision, who);
        }
        return orders.save(Order.create(id, amountCents));
    }

    /** 未命中走业务错全量交代（order.not-found → 400）。 */
    @Operation(operationId = "order.get", summary = "读取订单")
    @GetMapping("/{id}")
    public Order get(@PathVariable String id) {
        return orders.findById(id).orElseThrow(() -> new FailureNoticeException(FailureNotice.business(
                "order.not-found", "订单不存在",
                "订单 " + id + " 不存在或已删除；请确认订单号后重试", who.current())));
    }
}
