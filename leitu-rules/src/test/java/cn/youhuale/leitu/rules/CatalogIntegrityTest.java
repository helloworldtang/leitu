package cn.youhuale.leitu.rules;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资产完整性检查——把「answered 的宣告权」从自觉交给机器（AGENTS.md 完成判据）。
 *
 * <ul>
 *   <li>catalog 枚举合法、id 唯一、依赖图无孤儿；</li>
 *   <li>answered 条目五件套指向的文件真实存在（问题页/代码包/规则类/金样本目录）；</li>
 *   <li>drafted 条目至少有成文的问题页；</li>
 *   <li>全仓库 markdown 相对链接全部有效——改路径不改引用，构建红灯（文档同步的系统保证）。</li>
 * </ul>
 */
class CatalogIntegrityTest {

    static final Path ROOT = Paths.get("..").toAbsolutePath().normalize();

    List<Catalog.Entry> entries() throws IOException {
        return Catalog.load(ROOT.resolve("catalog/problems.json"));
    }

    @Test
    void 枚举值合法且id唯一() throws IOException {
        List<Catalog.Entry> all = entries();
        Set<String> ids = new HashSet<>();
        for (Catalog.Entry e : all) {
            assertTrue(ids.add(e.id()), "id 重复：" + e.id());
            assertTrue(Set.of("pipeline", "capability").contains(e.facet()), e.id() + " facet 非法：" + e.facet());
            assertTrue(Set.of("always", "conditional").contains(e.required()), e.id() + " required 非法：" + e.required());
            assertTrue(Set.of("open", "drafted", "answered", "deprecated", "removed").contains(e.status()),
                    e.id() + " status 非法：" + e.status());
            assertTrue(e.stability() == null || Set.of("frozen", "stable", "experimental").contains(e.stability()),
                    e.id() + " stability 非法：" + e.stability());
        }
    }

    @Test
    void depends_on引用的id都存在() throws IOException {
        Set<String> ids = new HashSet<>();
        entries().forEach(e -> ids.add(e.id()));
        for (Catalog.Entry e : entries()) {
            for (String dep : e.dependsOn()) {
                assertTrue(ids.contains(dep), e.id() + " 依赖了不存在的条目：" + dep);
            }
        }
    }

    @Test
    void answered条目五件套真实存在() throws IOException {
        for (Catalog.Entry e : entries()) {
            if (!"answered".equals(e.status())) {
                continue;
            }
            List<String> problems = fiveKitProblems(e, ROOT);
            assertTrue(problems.isEmpty(), e.id() + "：" + String.join("；", problems));
        }
    }

    /**
     * 五件套判据——<b>对任意根目录可复用</b>，返回问题清单（空=通过）。
     *
     * <p>抽出来的唯一理由：让判据能被反向自测调用。存在性断言最典型的退化是"永远绿"——
     * 路径算错、目录扫不到、判据写反，都会静默通过，而没人发现这把锁其实没锁上。
     * 没有反向自测，"answered 的宣告权在机器"就只是一句宣言。
     */
    static List<String> fiveKitProblems(Catalog.Entry e, Path root) throws IOException {
        List<String> problems = new ArrayList<>();
        if (e.stability() == null) {
            problems.add("answered 必须声明 stability");
        }
        if (e.doc() == null) {
            problems.add("缺 links.doc（问题页）");
        }
        if (e.code() == null) {
            problems.add("缺 links.code");
        }
        if (e.rule() == null) {
            problems.add("缺 links.rule（守护规则）");
        }
        if (e.example() == null) {
            problems.add("缺 links.example（金样本）");
        }
        if (e.doc() != null && !Files.exists(root.resolve(e.doc()))) {
            problems.add("问题页不存在 " + e.doc());
        }
        if (e.code() != null) {
            String[] code = e.code().split(":", 2);
            if (code.length != 2) {
                problems.add("code 格式应为 模块:包名，当前 " + e.code());
            } else {
                Path pkg = root.resolve(code[0] + "/src/main/java/" + code[1].replace('.', '/'));
                if (!Files.exists(pkg)) {
                    problems.add("代码包不存在 " + e.code());
                }
            }
        }
        if (e.rule() != null) {
            String[] rule = e.rule().split(":", 2);
            if (rule.length != 2 || locate(root, rule[0], rule[1] + ".java") == null) {
                problems.add("守护规则类不存在 " + e.rule());
            }
        }
        if (e.example() != null) {
            Path example = root.resolve(e.example());
            if (!Files.exists(example)) {
                problems.add("金样本目录不存在 " + e.example());
            } else if (!Files.exists(example.resolve("src/test/java"))) {
                // 只验"目录存在"会让一个空目录拿到 answered——金样本必须真的能跑
                problems.add("金样本目录里没有 src/test/java：只有 README 的空目录不算金样本 " + e.example());
            }
        }
        return problems;
    }

    /**
     * <b>反向自测</b>：故意造出缺件的 answered，确认判据真的会红。
     *
     * <p>三例分别覆盖三类退化——缺金样本目录、金样本是个空壳、缺守护规则。
     * 任何一条不再报红，就意味着这把锁已经失效，而主测试仍会静默绿。
     */
    @Test
    void 反向自测_五件套缺件必然报红(@TempDir Path tmp) throws IOException {
        // 先在临时根里把五件套造齐（问题页 / 代码包 / 规则类 / 金样本含测试）
        Catalog.Entry ok = new Catalog.Entry("neg.ok", "反向自测基线", "pipeline", "always",
                "answered", "stable", List.of(),
                "docs/problems/neg.md",
                "leitu-core:cn.youhuale.leitu.core.context",
                "leitu-core:CoreBoundaryRulesTest",
                "examples/neg");
        writeFiveKit(tmp, ok, true);
        assertTrue(fiveKitProblems(ok, tmp).isEmpty(),
                "五件套齐全时判据必须放行——否则反向自测本身是错的：" + fiveKitProblems(ok, tmp));

        // 1) 缺金样本目录（造齐后再把整个目录树删掉）
        Catalog.Entry noExample = withExample(ok, "examples/missing");
        writeFiveKit(tmp, noExample, true);
        deleteTree(tmp.resolve("examples/missing"));
        assertRed(fiveKitProblems(noExample, tmp), "金样本目录不存在");

        // 2) 金样本是个空壳（只有目录，没有 src/test/java）
        Catalog.Entry hollow = withExample(ok, "examples/hollow");
        writeFiveKit(tmp, hollow, false);
        assertRed(fiveKitProblems(hollow, tmp), "空目录");

        // 3) 缺守护规则
        Catalog.Entry noRule = new Catalog.Entry("neg.norule", "反向自测-缺规则", "pipeline", "always",
                "answered", "stable", List.of(),
                "docs/problems/neg.md",
                "leitu-core:cn.youhuale.leitu.core.context",
                "leitu-core:NoSuchRulesTest",
                "examples/neg");
        writeFiveKit(tmp, noRule, true);
        Files.delete(tmp.resolve("leitu-core/src/test/java/NoSuchRulesTest.java"));
        assertRed(fiveKitProblems(noRule, tmp), "守护规则类不存在");
    }

    /** 删掉整棵目录树（Files.delete 对非空目录会拒绝）。 */
    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static Catalog.Entry withExample(Catalog.Entry base, String example) {
        return new Catalog.Entry(base.id(), base.problem(), base.facet(), base.required(), base.status(),
                base.stability(), base.dependsOn(), base.doc(), base.code(), base.rule(), example);
    }

    /** 在 root 下按条目的 links 造出对应文件；withTests=false 时金样本只留空目录。 */
    private static void writeFiveKit(Path root, Catalog.Entry e, boolean withTests) throws IOException {
        Path doc = root.resolve(e.doc());
        Files.createDirectories(doc.getParent());
        Files.writeString(doc, "# 反向自测占位\n");

        String[] code = e.code().split(":", 2);
        Files.createDirectories(root.resolve(code[0] + "/src/main/java/" + code[1].replace('.', '/')));

        String[] rule = e.rule().split(":", 2);
        Files.createDirectories(root.resolve(rule[0] + "/src/test/java"));
        Files.writeString(root.resolve(rule[0] + "/src/test/java/" + rule[1] + ".java"), "// 占位\n");

        Path example = root.resolve(e.example());
        Files.createDirectories(example);
        if (withTests) {
            Files.createDirectories(example.resolve("src/test/java"));
        }
    }

    private static void assertRed(List<String> problems, String keyword) {
        assertFalse(problems.isEmpty(), "守卫失效：缺件（" + keyword + "）竟然判为通过");
        assertTrue(problems.stream().anyMatch(p -> p.contains(keyword)),
                "报红指向了别处（" + keyword + "）：" + problems);
    }

    @Test
    void drafted条目至少有成文的问题页() throws IOException {
        for (Catalog.Entry e : entries()) {
            if ("drafted".equals(e.status())) {
                assertTrue(e.doc() != null && Files.exists(ROOT.resolve(e.doc())),
                        e.id() + "：drafted=答案已成文——问题页必须已存在（只有方向没有问题页的用 open）");
            }
        }
    }

    @Test
    void markdown相对链接全部有效() throws IOException {
        List<String> broken = new java.util.ArrayList<>();
        try (Stream<Path> mdFiles = markdownFiles()) {
            mdFiles.forEach(md -> {
                String src;
                try {
                    src = Files.readString(md);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\]\\(([^)\\s]+)\\)").matcher(src);
                while (m.find()) {
                    String target = m.group(1);
                    if (target.startsWith("http") || target.startsWith("#") || target.startsWith("mailto")) {
                        continue;
                    }
                    Path resolved = md.getParent().resolve(target).normalize();
                    if (!Files.exists(resolved)) {
                        broken.add(md.getParent().relativize(ROOT) + "/" + md.getFileName() + " → " + target);
                    }
                }
            });
        }
        assertTrue(broken.isEmpty(), "失效的相对链接（改路径必须同提交改引用）：\n" + String.join("\n", broken));
    }

    Stream<Path> markdownFiles() throws IOException {
        return Files.walk(ROOT)
                .filter(p -> {
                    String s = p.toString();
                    return s.endsWith(".md")
                            && !s.contains("/.git/")
                            && !s.contains("/target/")
                            && !s.contains("node_modules");
                });
    }

    static Path locate(Path root, String module, String fileName) throws IOException {
        Path base = root.resolve(module + "/src/test/java");
        if (!Files.exists(base)) {
            return null;
        }
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(p -> p.getFileName().toString().equals(fileName)).findFirst().orElse(null);
        }
    }
}
