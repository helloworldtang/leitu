package cn.youhuale.leitu.rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * catalog/problems.json 的最小读取器——资产完整性检查的数据源。
 *
 * <p>只服务本仓库固定 schema 的 pretty JSON；schema 变更时读取器大声失败，
 * 由 CatalogIntegrityTest 兜底（scripts/ExportArchitecture.java 另持一份独立读取器，
 * 漂移由 CI 的视图 staleness 检查覆盖）。
 */
public final class Catalog {

    /** 一条问题目录条目（完整性检查所需的字段子集）。 */
    public record Entry(String id, String problem, String facet, String required, String status,
                        String stability, List<String> dependsOn,
                        String doc, String code, String rule, String example) {
    }

    private Catalog() {
    }

    public static List<Entry> load(Path catalogFile) throws IOException {
        if (!Files.exists(catalogFile)) {
            throw new IllegalStateException("未找到 " + catalogFile
                    + "。测试需在模块目录下运行（./mvnw verify 保障工作目录）");
        }
        List<Entry> out = new ArrayList<>();
        Cur c = new Cur();
        for (String line : Files.readString(catalogFile).split("\n", -1)) {
            String id = str(line, "id");
            if (id != null) {
                if (c.id != null) {
                    out.add(c.mount());
                }
                c = new Cur();
                c.id = id;
                continue;
            }
            if (c.id == null) {
                continue;
            }
            if (c.problem == null) c.problem = str(line, "problem");
            if (c.facet == null) c.facet = str(line, "facet");
            if (c.required == null) c.required = str(line, "required");
            if (c.status == null) c.status = str(line, "status");
            if (c.stability == null) c.stability = str(line, "stability");
            if (c.doc == null) c.doc = str(line, "doc");
            if (c.code == null) c.code = str(line, "code");
            if (c.rule == null) c.rule = str(line, "rule");
            if (c.example == null) c.example = str(line, "example");
            List<String> d = arr(line, "depends_on");
            if (d != null) {
                c.deps.addAll(d);
            }
        }
        if (c.id != null) {
            out.add(c.mount());
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("catalog 解析结果为空——schema 变了？请同步 Catalog 读取器");
        }
        return out;
    }

    static final class Cur {
        String id;
        String problem;
        String facet;
        String required;
        String status;
        String stability;
        String doc;
        String code;
        String rule;
        String example;
        final List<String> deps = new ArrayList<>();

        Entry mount() {
            if (problem == null || facet == null || required == null || status == null) {
                throw new IllegalStateException("条目 " + id + " 字段不完整——catalog schema 变了？请同步 Catalog 读取器");
            }
            return new Entry(id, problem, facet, required, status, stability, List.copyOf(deps),
                    doc, code, rule, example);
        }
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
}
