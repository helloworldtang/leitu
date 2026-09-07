package cn.youhuale.leitu.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
            assertTrue(e.stability() != null, e.id() + "：answered 必须声明 stability");
            assertTrue(e.doc() != null, e.id() + "：answered 缺 links.doc（问题页）");
            assertTrue(e.code() != null, e.id() + "：answered 缺 links.code");
            assertTrue(e.rule() != null, e.id() + "：answered 缺 links.rule（守护规则）");
            assertTrue(e.example() != null, e.id() + "：answered 缺 links.example（金样本）");

            assertTrue(Files.exists(ROOT.resolve(e.doc())), e.id() + "：问题页不存在 " + e.doc());

            String[] code = e.code().split(":", 2);
            assertTrue(code.length == 2, e.id() + "：code 格式应为 模块:包名，当前 " + e.code());
            Path pkg = ROOT.resolve(code[0] + "/src/main/java/" + code[1].replace('.', '/'));
            assertTrue(Files.exists(pkg), e.id() + "：代码包不存在 " + pkg);

            String[] rule = e.rule().split(":", 2);
            assertTrue(locate(rule[0], rule[1] + ".java") != null,
                    e.id() + "：守护规则类 " + e.rule() + " 不存在");

            assertTrue(Files.exists(ROOT.resolve(e.example())), e.id() + "：金样本目录不存在 " + e.example());
        }
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

    Path locate(String module, String fileName) throws IOException {
        Path base = ROOT.resolve(module + "/src/test/java");
        if (!Files.exists(base)) {
            return null;
        }
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(p -> p.getFileName().toString().equals(fileName)).findFirst().orElse(null);
        }
    }
}
