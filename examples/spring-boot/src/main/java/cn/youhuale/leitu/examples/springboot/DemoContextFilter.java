package cn.youhuale.leitu.examples.springboot;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 演示用上下文绑定：从请求头解析（X-Demo-User / X-Demo-Tenant / X-Demo-Trace），缺省匿名。
 * （不叫 RequestContextFilter——避开 Boot 内置同名 Bean，默认配置下同名会拒绝启动。）
 * 真实项目在此处接入认证体系（身份解析属 adapter / 接入口的事，见 catalog 条目 identity）。
 */
@Component
public class DemoContextFilter extends OncePerRequestFilter {

    private final ExecutionContextBinder binder;

    public DemoContextFilter(ExecutionContextBinder binder) {
        this.binder = binder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Operator operator = Operator.human(
                header(request, "X-Demo-User", "anonymous"),
                header(request, "X-Demo-Tenant", "-"));
        String trace = request.getHeader("X-Demo-Trace");
        ExecutionContext context = trace == null || trace.isBlank()
                ? ExecutionContext.of(operator)
                : ExecutionContext.of(operator, trace);
        try (var scope = binder.bind(context)) {
            chain.doFilter(request, response);
        }
    }

    private static String header(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
