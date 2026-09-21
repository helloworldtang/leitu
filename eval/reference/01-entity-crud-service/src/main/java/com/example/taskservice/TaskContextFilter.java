package com.example.taskservice;

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
 * 入口绑定：从请求头解析操作者（探针简化版；真实项目在此接入认证体系）。
 * 命名避开 Boot 内置 requestContextFilter Bean。
 */
@Component
public class TaskContextFilter extends OncePerRequestFilter {

    private final ExecutionContextBinder binder;

    public TaskContextFilter(ExecutionContextBinder binder) {
        this.binder = binder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Operator operator = Operator.human(
                header(request, "X-Probe-User", "anonymous"),
                header(request, "X-Probe-Tenant", "-"));
        String trace = request.getHeader("X-Probe-Trace");
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
