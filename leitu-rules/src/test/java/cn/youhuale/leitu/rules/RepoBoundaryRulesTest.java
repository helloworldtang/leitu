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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            if (!"cn.youhuale".equals(ownGroupId(modulePom))) {
                continue;   // 外来 groupId = 下游探针（如 eval/reference/*）：它是被检查的下游工程，不是本库产物
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

    /**
     * 每个待发布模块都必须出现在根 pom 的 {@code <dependencyManagement>} 里（#22）。
     *
     * <p>理由：下游用法是 {@code import cn.youhuale:leitu-parent:<版本>}（scope=import）拿到
     * 全部版本号——漏一个模块，下游用它就得自己手写 version，而手写的版本号迟早漂。
     * 此前 leitu-rules 就漏了：它正是"把规则带给下游"的那个模块，却偏偏是唯一要写版本的。
     *
     * <p>examples/* 不参与发布，因此不在本守卫的范围里。
     */
    @Test
    void 每个待发布模块都在dependencyManagement里_下游才能免版本号() throws IOException {
        Path root = CatalogIntegrityTest.ROOT;
        Set<String> managed = managedArtifactIds(root.resolve("pom.xml"));

        for (String dir : rootModules(root.resolve("pom.xml"))) {
            if (dir.startsWith("examples/")) {
                continue;   // 示例不发布
            }
            Path modulePom = root.resolve(dir).resolve("pom.xml");
            if (!Files.exists(modulePom)) {
                continue;
            }
            if (!"cn.youhuale".equals(ownGroupId(modulePom))) {
                continue;   // 下游探针不是本库产物
            }
            String artifactId = ownArtifactId(modulePom);
            assertTrue(managed.contains(artifactId),
                    "模块 " + dir + "（" + artifactId + "）不在根 pom 的 <dependencyManagement> 里——"
                            + "下游 import leitu-parent 后拿不到它的版本，只能手写，"
                            + "而手写的版本号是未来某次升级事故的起点。"
                            + "修复：在根 pom 的 dependencyManagement 补一条该模块的版本条目");
        }
    }

    /**
     * 反向自测：抽取逻辑本身必须有效——不然上面两条会变成"解析失败 = 一切正常"的恒绿。
     * 这里直接对着根 pom 断言"能抽出不少于 7 个本库条目"，且 leitu-core 必定在其中。
     */
    @Test
    void dependencyManagement解析出来了_守门人不是恒绿() throws IOException {
        Set<String> managed = managedArtifactIds(CatalogIntegrityTest.ROOT.resolve("pom.xml"));
        assertTrue(managed.size() >= 7, "根 pom 的 dependencyManagement 解析异常，只抽到 " + managed.size() + " 个");
        assertTrue(managed.contains("leitu-core"), "leitu-core 应当在 dependencyManagement 里：" + managed);
    }

    /**
     * starter 的「可选集成」必须标 optional：任何引 leitu-spring-boot-starter 的人，都不该被
     * 顺带塞进一整套接口文档套件（springdoc + knife4j + webjar 前端资源，几十 MB）——
     * 那是使用者的选择，不是这个 starter 的契约。
     *
     * <p>判据用结构化正则（artifactId 后面紧跟 {@code <optional>true</optional>}），
     * 不用 {@code contains("optional")}：pom 注释里就写着 optional 这个词，
     * 用 contains 会被注释喂成恒绿（同「守卫自己的判据也要能失败」那条）。
     */
    @Test
    void starter的可选集成必须标optional_不把文档套件强塞给使用者() throws IOException {
        String text = Files.readString(
                CatalogIntegrityTest.ROOT.resolve("leitu-spring-boot-starter/pom.xml"));
        for (String artifact : new String[]{
                "springdoc-openapi-starter-webmvc-ui",
                "knife4j-openapi3-boot4-spring-boot-starter"}) {
            Matcher m = Pattern.compile(
                            "<artifactId>" + Pattern.quote(artifact) + "</artifactId>\\s*<optional>true</optional>",
                            Pattern.DOTALL)
                    .matcher(text);
            assertTrue(m.find(),
                    "leitu-spring-boot-starter 对 " + artifact + " 必须标 <optional>true</optional>——"
                            + "不标的话每个引 starter 的项目都会被传递拖入整套接口文档套件。"
                            + "需要它的使用者自己显式声明（examples/spring-boot 就是这么做的）。");
        }
    }

    /**
     * BOM 自有模块的版本必须用显式属性 {@code <leitu.version>}，禁止 {@code ${project.version}}。
     *
     * <p>根 pom 被两种姿势消费：import BOM（{@code ${project.version}} 按 BOM 自身求值，正确）
     * 与 {@code <parent>} 继承——继承时它会在<b>使用者项目</b>里求值（= 使用者的版本号），
     * Maven 拿着这个版本去仓库找 {@code leitu-core:<使用者的版本>}，解析必失败。
     * 2026-09-23 外部探针实测复现（干净仓库从 Central 拉包，报
     * {@code Could not find artifact cn.youhuale:leitu-spring-boot-starter:jar:1.0.0}）。
     * Spring Boot 官方 BOM 同判据：spring-boot-dependencies 里 {@code ${project.version}} 出现 0 次，全硬编码。
     */
    @Test
    void BOM自有模块版本用显式属性_禁止projectVersion表达式() throws IOException {
        String text = Files.readString(CatalogIntegrityTest.ROOT.resolve("pom.xml"));
        String dm = dependencyManagement段(text);
        assertTrue(dm.contains("<version>${leitu.version}</version>"),
                "根 pom dependencyManagement 里应使用 <version>${leitu.version}</version> 引用自有模块版本。");
        assertTrue(!dm.contains("<version>${project.version}</version>"),
                "根 pom dependencyManagement 禁止 <version>${project.version}</version>——"
                        + "被当 <parent> 继承时该表达式在使用者项目里求值，"
                        + "下游会拿着自己的版本号来解析 leitu 模块，必然 Could not find artifact。"
                        + "修复：改用 <version>${leitu.version}</version>。");
        assertEquals(项目版本(text), leitu版本属性(text),
                "<leitu.version> 与 project.version 不一致——发版时 versions:set 不会更新这个属性，"
                        + "漏改会让 BOM 指向旧版本。同步：versions:set-property -Dproperty=leitu.version");
    }

    /** 反向自测：引用计数与版本抽取必须真实有效，否则上一条守门退化为恒绿。 */
    @Test
    void 自有模块版本引用解析出来了_守门人不是恒绿() throws IOException {
        String text = Files.readString(CatalogIntegrityTest.ROOT.resolve("pom.xml"));
        long refs = Pattern.compile(Pattern.quote("<version>${leitu.version}</version>"))
                .matcher(text).results().count();
        assertTrue(refs >= 8,
                "根 pom 里应至少 8 处 <version>${leitu.version}</version>（8 个待发布模块），实际 " + refs
                        + "——抽取计数坏了，BOM 版本守门会恒绿。");
        assertTrue(!项目版本(text).isBlank() && !leitu版本属性(text).isBlank(),
                "project.version 与 leitu.version 都必须能从根 pom 抽出非空值，否则一致性断言恒绿。");
    }

    /** dependencyManagement 段原文（含全部嵌套），供逐字符断言标签形态。 */
    private static String dependencyManagement段(String text) throws IOException {
        Matcher m = Pattern.compile("<dependencyManagement>(.*?)</dependencyManagement>", Pattern.DOTALL)
                .matcher(text);
        assertTrue(m.find(), "根 pom 里应存在 <dependencyManagement>");
        return m.group(1);
    }

    /** 顶层 project version：锚定两空格缩进（顶层元素），避开 dependencyManagement 里的深缩进条目。 */
    private static String 项目版本(String text) throws IOException {
        Matcher m = Pattern.compile("(?m)^  <version>([^<]+)</version>").matcher(text);
        assertTrue(m.find(), "根 pom 顶层 <version> 抽取失败");
        return m.group(1).trim();
    }

    /** properties 里的 leitu.version 属性值（锚定四空格缩进）。 */
    private static String leitu版本属性(String text) throws IOException {
        Matcher m = Pattern.compile("(?m)^    <leitu\\.version>([^<]+)</leitu\\.version>").matcher(text);
        assertTrue(m.find(), "根 pom properties 里应存在 <leitu.version> 属性");
        return m.group(1).trim();
    }

    private static Set<String> managedArtifactIds(Path parentPom) throws IOException {
        String text = Files.readString(parentPom);
        Matcher dm = Pattern.compile("<dependencyManagement>(.*?)</dependencyManagement>", Pattern.DOTALL)
                .matcher(text);
        Set<String> out = new HashSet<>();
        while (dm.find()) {
            Matcher artifact = Pattern.compile(
                            "<groupId>cn\\.youhuale</groupId>\\s*<artifactId>([^<]+)</artifactId>")
                    .matcher(dm.group(1));
            while (artifact.find()) {
                out.add(artifact.group(1).trim());
            }
        }
        return out;
    }

    /** 模块自己的 groupId（同样先摘掉 parent 块）；没有显式声明即继承父 pom 的 cn.youhuale。 */
    private static String ownGroupId(Path modulePom) throws IOException {
        String text = Files.readString(modulePom).replaceAll("(?s)<parent>.*?</parent>", "");
        Matcher m = Pattern.compile("<groupId>([^<]+)</groupId>").matcher(text);
        int start = m.find() ? m.start() : Integer.MAX_VALUE;
        Matcher buildGroupId = Pattern.compile("<build>.*?<groupId>([^<]+)</groupId>", Pattern.DOTALL)
                .matcher(text);
        if (buildGroupId.find() && buildGroupId.start() < start) {
            return buildGroupId.group(1).trim();
        }
        return m.find() ? m.group(1).trim() : "cn.youhuale";
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
