package cn.youhuale.leitu.adapter.web;

import java.util.List;

/**
 * 框架端点谓词：API 文档 / UI 等"非业务端点"的路径前缀清单——
 * 供使用方的统一响应包装组件豁免（验尸教训的防再犯，见 ADR-013 增值点 4）。
 *
 * <p>用法（例）：
 * <pre>{@code
 * if (FrameworkEndpoints.matches(request.getRequestURI())) {
 *     return body;   // 非业务端点不包装
 * }
 * }</pre>
 */
public final class FrameworkEndpoints {

    private static final List<String> PREFIXES = List.of(
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources",
            "/doc.html",
            "/webjars");

    private FrameworkEndpoints() {
    }

    /** 是否框架端点（按路径前缀判定）。 */
    public static boolean matches(String path) {
        if (path == null) {
            return false;
        }
        for (String prefix : PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 前缀清单（诊断/文档用，只读）。 */
    public static List<String> prefixes() {
        return PREFIXES;
    }
}
