import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 架构状态导出：catalog → 4R 四图（Rank / Role / Relation / Rule）。
 *
 * <p>用法（JDK 单文件源码直接运行，无需构建无需额外依赖）：在仓库根目录执行
 * <pre>java scripts/ExportArchitecture.java</pre>
 * 产出：docs/architecture/views.md——「10 分钟看懂现状」的可审计性工件。
 * 本工具是 views.md 的唯一生成器；views.md 为生成物，勿手改。
 *
 * <p>读取器说明：只服务本仓库固定 schema 的 pretty JSON（catalog/problems.json）。
 * catalog 结构变更时读取器会大声失败——CI 的 staleness 检查兜底。
 */
public class ExportArchitecture {

    record Entry(String id, String problem, String facet, String required, String status, List<String> dependsOn) {
    }

    static final Path ROOT = Paths.get(System.getProperty("user.dir"));
    static final Path CATALOG = ROOT.resolve("catalog/problems.json");
    static final List<Path> RULES_FILES = List.of(
            ROOT.resolve("leitu-core/src/test/java/cn/youhuale/leitu/core/CoreBoundaryRulesTest.java"),
            ROOT.resolve("leitu-rules/src/main/java/cn/youhuale/leitu/rules/LeituRules.java"));
    static final Path OUT = ROOT.resolve("docs/architecture/views.md");

    public static void main(String[] args) throws IOException {
        if (!Files.exists(CATALOG)) {
            System.err.println("未找到 catalog/problems.json。请在仓库根目录运行：java scripts/ExportArchitecture.java");
            System.exit(1);
        }
        List<Entry> entries = parseEntries(Files.readString(CATALOG));
        List<String[]> rules = new ArrayList<>();
        for (Path rulesFile : RULES_FILES) {
            if (Files.exists(rulesFile)) {
                rules.addAll(parseRules(Files.readString(rulesFile)));
            }
        }
        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, render(entries, rules));
        System.out.println("✓ docs/architecture/views.md（" + entries.size() + " 条目，" + rules.size() + " 规则）");
    }

    // ---- catalog 读取（容错：抓不到预期结构即失败并指路） ----

    static List<Entry> parseEntries(String json) {
        List<Entry> out = new ArrayList<>();
        String id = null, problem = null, facet = null, required = null, status = null;
        List<String> deps = new ArrayList<>();
        for (String line : json.split("\n", -1)) {
            String v = str(line, "id");
            if (v != null) {
                if (id != null) {
                    out.add(mount(id, problem, facet, required, status, deps));
                }
                id = v;
                problem = facet = required = status = null;
                deps = new ArrayList<>();
                continue;
            }
            if (id == null) {
                continue;
            }
            if (problem == null) problem = str(line, "problem");
            if (facet == null) facet = str(line, "facet");
            if (required == null) required = str(line, "required");
            if (status == null) status = str(line, "status");
            List<String> d = arr(line, "depends_on");
            if (d != null) deps.addAll(d);
        }
        if (id != null) {
            out.add(mount(id, problem, facet, required, status, deps));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("catalog 解析结果为空——schema 变了？请同步 scripts/ExportArchitecture.java 的读取器");
        }
        return out;
    }

    static Entry mount(String id, String problem, String facet, String required, String status, List<String> deps) {
        if (problem == null || facet == null || required == null || status == null) {
            throw new IllegalStateException("条目 " + id + " 字段不完整——catalog schema 变了？请同步读取器");
        }
        return new Entry(id, problem, facet, required, status, List.copyOf(deps));
    }

    static String str(String line, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*\"([^\"]*)\"").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    static List<String> arr(String line, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\\s*\\[([^]]*)]").matcher(line);
        if (!m.find()) {
            return null;
        }
        List<String> vals = new ArrayList<>();
        Matcher vm = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
        while (vm.find()) {
            vals.add(vm.group(1));
        }
        return vals;
    }

    static List<String[]> parseRules(String src) {
        List<String[]> out = new ArrayList<>();
        // 剥注释（javadoc 里出现 "static final ArchRule r = ..." 示例会污染解析）
        String clean = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        // 委托字段（= helper("modulePath")）的消息模板：取 helper 的 because 拼接式
        Matcher helper = Pattern.compile("because\\(\"([^\"]+)\"\\s*\\+\\s*modulePath\\s*\\+\\s*\"([^\"]+)\"\\)").matcher(clean);
        String delegateTemplate = helper.find() ? helper.group(1) + "{0}" + helper.group(2) : null;
        Matcher m = Pattern.compile("static final ArchRule\\s+([\\p{L}\\p{N}_]+)\\s*=(.*?);", Pattern.DOTALL).matcher(clean);
        while (m.find()) {
            String name = m.group(1);
            String body = m.group(2);
            Matcher b = Pattern.compile("because\\(\"([^\"]+)\"\\)").matcher(body);
            if (b.find()) {
                out.add(new String[]{name, b.group(1)});
                continue;
            }
            Matcher d = Pattern.compile("[\\p{L}\\p{N}_]+\\(\"([^\"]+)\"\\)").matcher(body);
            if (d.find() && delegateTemplate != null) {
                out.add(new String[]{name, delegateTemplate.replace("{0}", d.group(1))});
            }
        }
        return out;
    }

    // ---- 渲染（4R 四图） ----

    static String render(List<Entry> entries, List<String[]> rules) {
        long answered = entries.stream().filter(e -> "answered".equals(e.status())).count();
        long pipeline = entries.stream().filter(e -> "pipeline".equals(e.facet())).count();
        long capability = entries.size() - pipeline;
        StringBuilder b = new StringBuilder();
        b.append("# 架构状态（4R 四图）\n\n")
                .append("> 自动生成（`java scripts/ExportArchitecture.java`），勿手改。")
                .append("数据源：catalog/problems.json + 边界规则测试（CoreBoundaryRulesTest + LeituRules）。\n\n");

        b.append("## Rank——顶层结构（双平面）\n\n```mermaid\nflowchart TB\n")
                .append("  subgraph code[代码平面]\n")
                .append("    EX[examples 使用面] --> ST[starter 装配面]\n")
                .append("    ST --> AD[adapter 绑定面] --> CA[capability 能力面] --> CO[core 端口面]\n")
                .append("  end\n")
                .append("  subgraph asset[资产平面（答案的多面表示）]\n")
                .append("    CAT[catalog 机器地图] --- DOC[docs/problems 答案页]\n")
                .append("    DOC --- DEC[decisions 决策链] --- RUL[rules 守护规则] --- EXA[examples 金样本]\n")
                .append("  end\n")
                .append("  CO -. 答案五件套锚定 .-> CAT\n```\n\n")
                .append("覆盖度：**").append(answered).append(" / ").append(entries.size())
                .append("** 已答（管道题 ").append(pipeline).append(" 条，能力题 ").append(capability).append(" 条）。\n\n");

        b.append("## Role——各区职责与状态\n\n| id | 面 | 档 | 状态 | 问题 |\n|---|---|---|---|---|\n");
        for (Entry e : entries) {
            b.append("| ").append(e.id()).append(" | ").append(e.facet()).append(" | ").append(e.required())
                    .append(" | ").append(icon(e.status())).append(' ').append(e.status())
                    .append(" | ").append(e.problem()).append(" |\n");
        }
        b.append('\n');

        b.append("## Relation——答案依赖图\n\n")
                .append("（AI 学习路径：沿箭头方向先学依赖根；答案级爆炸半径：改动被依赖多的节点前先看下游）\n\n")
                .append("```mermaid\ngraph LR\n");
        Set<String> nodeIds = new LinkedHashSet<>();
        for (Entry e : entries) {
            for (String dep : e.dependsOn()) {
                b.append("  ").append(e.id()).append(" --> ").append(dep).append('\n');
                nodeIds.add(e.id());
                nodeIds.add(dep);
            }
        }
        for (Entry e : entries) {
            if (nodeIds.contains(e.id()) && "answered".equals(e.status())) {
                b.append("  class ").append(e.id()).append(" answered\n");
            }
        }
        b.append("  classDef answered fill:#d4edda,stroke:#2e7d32\n```\n\n");

        b.append("## Rule——守护规则清单\n\n| 规则 | 守什么 |\n|---|---|\n");
        for (String[] r : rules) {
            b.append("| `").append(r[0]).append("` | ").append(r[1]).append(" |\n");
        }
        b.append("| `./mvnw verify` | 编译 + 测试 + 边界规则一条命令（AGENTS.md 完成判据） |\n")
                .append("| `java scripts/ExportArchitecture.java` | 本视图的再生成（catalog 变更后跑） |\n");
        return b.toString();
    }

    static String icon(String status) {
        return switch (status) {
            case "answered" -> "✅";
            case "drafted" -> "📝";
            case "open" -> "⬜";
            case "deprecated" -> "🗑";
            case "removed" -> "❌";
            default -> "？";
        };
    }
}
