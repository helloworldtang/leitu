package cn.youhuale.leitu.adapter.web;

import java.util.List;

/**
 * 框架端点谓词：API 文档 / UI 等"非业务端点"的路径清单——
 * 供使用方的统一响应包装组件豁免（验尸教训的防再犯，见 ADR-013 增值点 4）。
 *
 * <p>用法（例）：
 * <pre>{@code
 * // 应用部署在 context-path 下时必须传 contextPath，否则豁免永不命中
 * if (FrameworkEndpoints.matches(request.getRequestURI(), request.getContextPath())) {
 *     return body;   // 非业务端点不包装
 * }
 * }</pre>
 *
 * <p><b>两个必须知道的坑</b>（都踩过）：
 * <ol>
 *   <li><b>context-path</b>：{@code request.getRequestURI()} 是含 context-path 的全路径
 *       （如 {@code /app/v3/api-docs}），直接拿它比 {@code /v3/api-docs} 永远比不上——
 *       于是文档端点被响应包装包了一层，swagger 解析失败，而现象看上去像"框架文档坏了"。
 *       用两参版剥离 context-path。</li>
 *   <li><b>前缀边界</b>：只做 {@code startsWith} 时 {@code /doc.htmlfoo} 也会被当成框架端点豁免——
 *       一个业务端点就此逃出统一包装，且只在有人给它起了个倒霉名字时才暴露。
 *       本类要求前缀之后必须是路径边界（{@code /}、{@code ?}、{@code #}）或就此结束。</li>
 * </ol>
 */
public final class FrameworkEndpoints {

    private static final List<String> PREFIXES = List.of(
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-ui.html",
            "/swagger-resources",
            "/doc.html",
            "/webjars");

    private FrameworkEndpoints() {
    }

    /**
     * 是否框架端点。
     *
     * <p>只在确定应用部署在根路径（context-path 为空或 "/"）时用这个重载；
     * 否则请用 {@link #matches(String, String)}——单参版在 context-path 下会静默失效。
     */
    public static boolean matches(String path) {
        return matches(path, null);
    }

    /** 是否框架端点（先剥离 contextPath，再按前缀边界判定）。 */
    public static boolean matches(String path, String contextPath) {
        if (path == null) {
            return false;
        }
        String stripped = stripContextPath(path, contextPath);
        for (String prefix : PREFIXES) {
            if (matchesPrefix(stripped, prefix)) {
                return true;
            }
        }
        return false;
    }

    /** 前缀清单（诊断/文档用，只读）。 */
    public static List<String> prefixes() {
        return PREFIXES;
    }

    private static String stripContextPath(String path, String contextPath) {
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return path;
        }
        return path.startsWith(contextPath) ? path.substring(contextPath.length()) : path;
    }

    /** 前缀命中且落在路径边界上——{@code /doc.htmlfoo} 不算 {@code /doc.html}。 */
    private static boolean matchesPrefix(String path, String prefix) {
        if (!path.startsWith(prefix)) {
            return false;
        }
        if (path.length() == prefix.length()) {
            return true;
        }
        char next = path.charAt(prefix.length());
        return next == '/' || next == '?' || next == '#';
    }
}
