package cn.youhuale.leitu.examples.springboot;

import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.context.spi.ExecutionContextBinder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 演示用上下文绑定：从请求头解析（X-Demo-User / X-Demo-Tenant / X-Demo-Trace），缺省匿名。
 * （不叫 RequestContextFilter——避开 Boot 内置同名 Bean，默认配置下同名会拒绝启动。）
 *
 * <p><b>这一段只在 {@code demo.trust-request-headers=true} 时生效，默认是关的。</b>
 * 请求头是调用方完全可控的输入：任何人都能发一个 {@code X-Demo-User: admin}，
 * 把"从请求头取身份"当成答案写进生产，等于把认证交给调用方自己填——
 * 这不是"简化"，是<b>把门禁拆了</b>。所以默认一律匿名，而匿名不参与角色判定
 * （见 {@code RoleMapGuard}）：没接认证时拿到的是 403，而不是"人人都能当管理员"。
 * 失败方向必须是安全侧。
 *
 * <p>真实项目在此处接入认证体系：认证通过后，用<b>已认证主体</b>构造 Operator
 * （身份解析属 adapter / 接入口的事，见 catalog 条目 identity）。
 */
@Component
public class DemoContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DemoContextFilter.class);

    private final ExecutionContextBinder binder;
    private final boolean trustHeaders;

    public DemoContextFilter(ExecutionContextBinder binder,
                             @Value("${demo.trust-request-headers:false}") boolean trustHeaders) {
        this.binder = binder;
        this.trustHeaders = trustHeaders;
        if (trustHeaders) {
            log.warn("演示模式：正在把请求头当作身份来源（X-Demo-User / X-Demo-Tenant）——"
                    + "请求头是调用方可任意伪造的输入，此模式严禁出现在生产环境。"
                    + "生产请在认证通过后用已认证主体构造 Operator");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Operator operator = trustHeaders
                ? Operator.human(
                        header(request, "X-Demo-User", "anonymous"),
                        header(request, "X-Demo-Tenant", "-"))
                : Operator.anonymous();
        String trace = trustHeaders ? request.getHeader("X-Demo-Trace") : null;
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
