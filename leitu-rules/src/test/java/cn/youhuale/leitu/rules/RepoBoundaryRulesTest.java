package cn.youhuale.leitu.rules;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * repo 级边界规则：扫描全 reactor（core / capability / adapter / starter / examples + 本模块）。
 * 与 leitu-core 内的 CoreBoundaryRulesTest（模块内快速反馈）互补——这里是全仓库的最终门。
 */
@AnalyzeClasses(packages = "cn.youhuale.leitu", importOptions = ImportOption.DoNotIncludeTests.class)
class RepoBoundaryRulesTest {

    @ArchTest
    static final ArchRule core零框架依赖 = LeituRules.CORE_零框架依赖;

    @ArchTest
    static final ArchRule internal只被core访问 = LeituRules.INTERNAL_只被core访问;

    @ArchTest
    static final ArchRule internal只被本能力模块访问 = LeituRules.INTERNAL_只被本能力模块访问;

    @ArchTest
    static final ArchRule internal只被本能力模块访问数据 = LeituRules.INTERNAL_只被本能力模块访问_数据;

    @ArchTest
    static final ArchRule internal只被本能力模块访问缓存 = LeituRules.INTERNAL_只被本能力模块访问_缓存;

    @ArchTest
    static final ArchRule examples相互独立 = LeituRules.EXAMPLES_相互独立;

    @ArchTest
    static final ArchRule spring只在starter与adapter = LeituRules.SPRING_只在starter与adapter;

    @ArchTest
    static final ArchRule internal只被本适配模块访问JDBC = LeituRules.INTERNAL_只被本适配模块访问_JDBC;

    @ArchTest
    static final ArchRule internal只被本适配模块访问WEB = LeituRules.INTERNAL_只被本适配模块访问_WEB;

    /**
     * 扫描范围交叉校验：<b>新增模块必须进得了 ArchUnit 的 classpath</b>。
     *
     * <p>ArchUnit 扫的是 {@code cn.youhuale.leitu} 包，但能扫到哪些类，取决于 leitu-rules
     * 的 test 依赖里列了哪些模块。新加模块时若只改根 pom 的 {@code <modules>}、忘了在
     * leitu-rules/pom.xml 补 test 依赖，它的类根本进不了扫描范围：没有规则、没有违规、
     * 没有失败——<b>退色绿</b>。这类"没写规则所以不违规"的洞，只有交叉校验能堵。
     */
    @Test
    void 每个模块都在守护网内_新模块不进扫描范围就红灯() throws IOException {
        Path root = CatalogIntegrityTest.ROOT;
        Set<String> guarded = guardedArtifactIds(root.resolve("leitu-rules/pom.xml"));
        Set<String> modules = rootModules(root.resolve("pom.xml"));
        assertTrue(modules.size() >= 8, "根 pom 模块解析异常，只解析到 " + modules.size() + " 个");

        for (String dir : modules) {
            Path modulePom = root.resolve(dir).resolve("pom.xml");
            if (!Files.exists(modulePom)) {
                continue;
            }
            String artifactId = ownArtifactId(modulePom);
            if ("leitu-rules".equals(artifactId)) {
                continue;   // 本模块无需把自己列为依赖
            }
            assertTrue(guarded.contains(artifactId),
                    "模块 " + dir + "（" + artifactId + "）不在 leitu-rules 的 test 依赖里——"
                            + "它的类进不了 ArchUnit 扫描范围，等于脱离守护网（退色绿）。"
                            + "修复：在 leitu-rules/pom.xml 补一条该模块的 test 依赖");
        }
    }

    private static Set<String> rootModules(Path parentPom) throws IOException {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(Files.readString(parentPom));
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }

    /** 模块自己的 artifactId（先摘掉 parent 块，免得取到 leitu-parent）。 */
    private static String ownArtifactId(Path modulePom) throws IOException {
        String text = Files.readString(modulePom).replaceAll("(?s)<parent>.*?</parent>", "");
        Matcher m = Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(text);
        return m.find() ? m.group(1).trim() : "";
    }

    private static Set<String> guardedArtifactIds(Path rulesPom) throws IOException {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("<groupId>cn\\.youhuale</groupId>\\s*<artifactId>([^<]+)</artifactId>")
                .matcher(Files.readString(rulesPom));
        while (m.find()) {
            out.add(m.group(1).trim());
        }
        return out;
    }
}
