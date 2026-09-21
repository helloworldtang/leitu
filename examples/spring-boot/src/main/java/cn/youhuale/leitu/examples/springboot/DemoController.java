package cn.youhuale.leitu.examples.springboot;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 演示系统错：脱敏交代 + traceId（细节走观测通道，不出端）。 */
@RestController
@RequestMapping("/demo")
public class DemoController {

    @Operation(operationId = "demo.boom", summary = "演示系统错")
    @GetMapping("/boom")
    public String boom() {
        throw new IllegalStateException("jdbc:mysql://prod-db:3306/order?user=root");
    }
}
