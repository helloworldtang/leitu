package cn.youhuale.leitu.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 文档一致性——把「数字过期但没人发现」这一类漂移交给机器，而不是交给阅读理解。
 *
 * <ul>
 *   <li>#26 README / ROADMAP 里写死的测试数量：写死的第二天就开始漂，因此禁止出现，
 *       数量由 CI 的测试结果回答；</li>
 *   <li>README 宣称的 ADR 范围必须与 docs/decisions 里的实际编号一致；</li>
 *   <li>#24 发布到 Maven Central 的强制元数据（url/licenses/developers/scm）齐全——
 *       缺哪条都是 Portal 校验拒收，且往往要等到发布流程走到一半才报。</li>
 * </ul>
 */
class DocsConsistencyTest {

    private static final Path ROOT = CatalogIntegrityTest.ROOT;

    /**
     * "191 个测试" / "191 测试全绿" 一类写法——数量由 CI 回答，文档不写死。
     *
     * <p>判据收紧到"计数 + 测试 + 结果"的完整搭配，避免误伤 "Boot 4 测试基建" 这类
     * 含数字但并非数量承诺的句子（误伤会让守卫变成另一种噪声）。
     */
    private static final Pattern HARDCODED_TEST_COUNT =
            Pattern.compile("\\d+\\s*个测试|\\d+\\s*个\\s*测试全绿|\\d+\\s*测试全绿");

    @Test
    void 文档不写死测试数量_数量由CI回答() throws IOException {
        List<Path> docs = new ArrayList<>();
        try (var walk = Files.walk(ROOT)) {
            docs.addAll(walk
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .toList());
        }
        for (Path doc : docs) {
            Matcher m = HARDCODED_TEST_COUNT.matcher(read(doc));
            if (m.find()) {
                fail("文档 " + ROOT.relativize(doc) + " 写死了测试数量（" + m.group() + "）："
                        + "写死的那一刻它就准，之后每次加测试它都错一点，而没有人会收到通知。"
                        + "修复：删掉数字，写「测试全绿（数量由 CI 校验）」——数量属于 CI 的回答，不属于文档");
            }
        }
    }

    /**
     * 文档里宣称的 ADR 范围必须对得上目录实际的最大编号（#26）。
     *
     * <p>README 一度写着 ADR-001~013，而目录里实际已经到 015；修完 README 后我又漏了
     * ROADMAP 导航里同样的一处——因为这一版的守卫只扫 README。守门人守得比它该守的窄，
     * 漏掉的那部分就退化成"靠人记得改"。所以这里改成扫全仓 markdown。
     *
     * <p><b>判据为何要带"当前态"过滤</b>：ROADMAP 阶段表里还有一句
     * "ADR-001~007 … ✅ 完成（2026-09-07）"——那是历史快照，说它错就等于逼人篡改史实。
     * 因此只在<b>该行不含日期</b>时才按"当前上限"校验：带日期的是阶段性回顾，不带日期的是导航。
     */
    @Test
    void 文档宣称的ADR范围必须覆盖目录里的最大编号() throws IOException {
        int max = 0;
        try (Stream<Path> files = Files.list(ROOT.resolve("docs/decisions"))) {
            for (Path f : files.toList()) {
                Matcher m = Pattern.compile("ADR-(\\d{3})").matcher(f.getFileName().toString());
                if (m.find()) {
                    max = Math.max(max, Integer.parseInt(m.group(1)));
                }
            }
        }
        assertTrue(max > 0, "docs/decisions 里没解析到任何 ADR 文件——测试脚手架失效");

        Pattern range = Pattern.compile("ADR-001~(\\d{3})");
        Pattern dated = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        List<Path> docs = new ArrayList<>();
        try (var walk = Files.walk(ROOT)) {
            docs.addAll(walk
                    .filter(pa -> pa.toString().endsWith(".md"))
                    .filter(pa -> !pa.toString().contains("/target/"))
                    .toList());
        }
        int checked = 0;
        for (Path doc : docs) {
            for (String line : read(doc).split("\\R")) {
                Matcher r = range.matcher(line);
                if (!r.find() || dated.matcher(line).find()) {
                    continue;   // 无范围表述，或这是带日期的历史快照
                }
                checked++;
                int claimed = Integer.parseInt(r.group(1));
                assertTrue(claimed == max,
                        "文档 " + ROOT.relativize(doc) + " 写的 ADR 上限是 " + claimed
                                + "，而 docs/decisions 里的实际最大编号是 " + max + "："
                                + "导航错了比不存在更坏——按导航去读的人会漏掉后面的决策。（原文：" + line.trim() + "）");
            }
        }
        assertTrue(checked >= 1, "全仓 markdown 里没找到任何 ADR 范围表述——守卫会不会是恒绿？");
    }

    /**
     * Central Portal 的强制元数据：缺失会在发布流程中段才报错，排查成本远高于一次断言。
     *
     * <p>判据用<b>结构化正则</b>而不是 {@code contains("<licenses>")}——后者会被注释里的同名
     * 字符串骗过去（我们自己的注释就在解释这四条缺了会怎样），守卫会变成恒绿。
     */
    @Test
    void 发布元数据存储_portal校验不会被拒收() throws IOException {
        String pom = read(ROOT.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>leitu-parent</artifactId>"), "这是根 pom 吗？");
        assertMatches(pom, "<url>[^<]+</url>", "项目主页 URL（Portal 展示用）");
        // <description> 是六项里最容易漏的一项：README 有介绍、pom 有 <name>，于是没人写它，
        // 而 Portal 的元数学校验要的就是这个字段本身。<name> 不能替代它。
        assertMatches(pom, "<name>[^<]+</name>", "项目名称");
        assertMatches(pom, "<description>[^<]+</description>", "项目描述（Portal 要求<｜hy_place▁holder▁no▁813｜>与 <name> 并存）");
        assertMatches(pom, "<licenses>\\s*<license>", "至少一条 license 条目");
        assertMatches(pom, "<licenses>(?s:.*?)<distribution>repo</distribution>",
                "license 需声明 distribution=repo（随包分发的许可）");
        assertMatches(pom, "<developers>\\s*<developer>", "至少一位 developer");
        assertMatches(pom, "<scm>\\s*<connection>", "SCM connection（只读地址）");
        assertMatches(pom, "<scm>(?s:.*?)<developerConnection>", "SCM developerConnection（可写地址）");
        assertTrue(Files.exists(ROOT.resolve("LICENSE")), "LICENSE 文件必须与 licenses 声明同时存在");
        assertTrue(read(ROOT.resolve("LICENSE")).contains("MIT"), "pom 声明 MIT 时 LICENSE 文件必须是 MIT 文本");
    }

    /**
     * 发布流程必须真的调用版本闸门（#23）。
     *
     * <p>这道守卫管的不是脚本本身的正确性，而是"流程有没有调用它"——发布流程是最容易被
     * 顺手改坏又没人复核的地方：一次临时绕过的 sed、一次工作流重构，闸门就静默消失了，
     * 而它的缺失只有在真的发错版本那天才会显现（那时已在 Central 上，撤回不了）。
     */
    @Test
    void 发布流程调用版本闸门_且校验脚本存在() throws IOException {
        Path script = ROOT.resolve("scripts/check-release-version.sh");
        assertTrue(Files.exists(script), "版本闸门脚本 scripts/check-release-version.sh 必须存在");

        String release = read(ROOT.resolve(".github/workflows/release.yml"));
        int gate = release.indexOf("check-release-version.sh");
        // 判据要锚到 YAML 的步骤定义行，不能只搜裸字符串：
        // v0.2.0 发版时我在 job env 的注释里写了「Publish to Maven Central」，indexOf 先匹配到
        // 注释（在文件上方），于是这道守卫把顺序正确的流程判成错的。
        // 用 ^\s*- name: 锚定行首：注释行第一个非空白字符是 #，不会被误匹配。
        // 同「守卫自己的判据也要能失败」那条——判据必须指向结构，不能指向会出现在注释里的文本。
        Matcher publishStep = Pattern.compile("(?m)^\\s*- name: Publish to Maven Central\\s*$")
                .matcher(release);
        int publish = publishStep.find() ? publishStep.start() : -1;
        assertTrue(gate > 0, "release.yml 必须调用 scripts/check-release-version.sh——"
                + "以便在 tag 与 pom 版本不一致、或版本仍是 SNAPSHOT 时终止发布");
        assertTrue(publish > 0 && gate < publish,
                "版本闸门必须排在发布步骤（Publish to Maven Central）之前：先校验再发布，别反过来");
    }

    /**
     * 发布插件不得低于 0.11.0：低于它，发布会以「产物已上传、构建却报失败」的形态挂掉。
     *
     * <p>原因（v0.2.0 第 5 次发布实测，非推断）：central-publishing 0.7.0 解析 Portal 部署状态
     * 用的 ObjectMapper 是构造器里裸 {@code new ObjectMapper()}，零配置，而 Jackson 的
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} 默认 true。Portal 只要新增响应字段（2026-09 起是
     * {@code warnings}），就会在「bundle 上传成功之后、读校验结果时」抛
     * UnrecognizedPropertyException——日志看着像发布失败，其实产物已在 Portal 上。
     * 0.11.0 起该 mapper 改为静态字段并显式
     * {@code configure(FAIL_ON_UNKNOWN_PROPERTIES, false)}，对未知字段免疫。
     *
     * <p>判据取「插件块内的 version」再做数值比较，不是断言"存在某个版本号"：
     * 最容易发生的事故是有人为了"稳"把版本回退到记得住的旧版，而代价要等下一次发版才显现。
     * 取块内而非紧邻匹配，是因为 artifactId 与 version 之间可能夹着说明注释。
     */
    @Test
    void 发布插件不得低于0_11_0_否则解析Portal响应时会炸() throws IOException {
        String pom = read(ROOT.resolve("pom.xml"));
        int at = pom.indexOf("central-publishing-maven-plugin");
        assertTrue(at > 0, "根 pom 必须声明 central-publishing-maven-plugin（release profile 的发布插件）");
        int end = pom.indexOf("</plugin>", at);
        String block = pom.substring(at, end > at ? end : pom.length());

        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(block);
        assertTrue(m.find(), "central-publishing-maven-plugin 必须显式声明版本号，不要靠外部管理，"
                + "否则本守卫无从判定它是否踩在已知会崩的区间里");
        String version = m.group(1).trim();

        assertTrue(compareVersions(version, "0.11.0") >= 0,
                "central-publishing-maven-plugin 是 " + version + "，低于 0.11.0——0.11.0 之前"
                        + "解析 Portal 响应用的是裸 ObjectMapper（Jackson 默认拒绝未知字段），"
                        + "Portal 新增响应字段（如 warnings）时会在 bundle 已上传成功之后抛 "
                        + "UnrecognizedPropertyException，表现为「包传上去了但构建报失败」。");
    }

    private static int compareVersions(String a, String b) {
        int[] x = versionParts(a);
        int[] y = versionParts(b);
        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i]) {
                return Integer.compare(x[i], y[i]);
            }
        }
        return 0;
    }

    private static int[] versionParts(String version) {
        String[] parts = version.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < out.length && i < parts.length; i++) {
            Matcher digits = Pattern.compile("^(\\d+)").matcher(parts[i].trim());
            out[i] = digits.find() ? Integer.parseInt(digits.group(1)) : 0;
        }
        return out;
    }

    private static void assertMatches(String pom, String regex, String why) {
        Pattern pattern = Pattern.compile(regex);
        assertTrue(pattern.matcher(pom).find(),
                "根 pom 缺 /" + regex + "/ 对应的内容：" + why
                        + "。修复：补齐 Central 强制元数据四项（url / licenses / developers / scm）");
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }
}
