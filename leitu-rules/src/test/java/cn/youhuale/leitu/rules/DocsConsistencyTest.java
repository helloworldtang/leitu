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
     * README 一度写着 ADR-001~013，而目录里实际已经到 015（#26）。
     * 这类"导航写错了"的小事最伤 AI：它按导航去读，以为只有 13 条决策。
     */
    @Test
    void README写的ADR范围必须覆盖目录里的最大编号() throws IOException {
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

        String readme = read(ROOT.resolve("README.md"));
        Matcher range = Pattern.compile("ADR-001~(\\d{3})").matcher(readme);
        assertTrue(range.find(), "README 应当标注 ADR 的范围（如 ADR-001~0" + max + "）");
        assertEqualsRange(max, Integer.parseInt(range.group(1)));
    }

    private static void assertEqualsRange(int actualMax, int claimed) {
        assertTrue(claimed == actualMax,
                "README 写的 ADR 上限 " + claimed + " 与目录里的实际最大编号 " + actualMax + " 不一致："
                        + "导航错了比不存在更坏——按导航去读的人会漏掉后面的决策");
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
        int publish = release.indexOf("Publish to Maven Central");
        assertTrue(gate > 0, "release.yml 必须调用 scripts/check-release-version.sh——"
                + "以便在 tag 与 pom 版本不一致、或版本仍是 SNAPSHOT 时终止发布");
        assertTrue(publish > 0 && gate < publish,
                "版本闸门必须排在发布步骤（Publish to Maven Central）之前：先校验再发布，别反过来");
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
