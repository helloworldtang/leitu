package cn.youhuale.leitu.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「文档点名的东西，必须真的存在」——成熟度自评（MATURITY.md 2026-11-02）里标为 P0 的两条缝，
 * 以及 P1 里能确定性化的四条事实。
 *
 * <p>为什么这一组值得单独一个类：前面几道守卫守的是「格式与元数据」（测试数量、ADR 范围、发布插件、
 * 链接可达），它们不会让照文档写代码的人失败；这一组守的是<b>文档内容本身能不能用</b>——
 * 点名的类型不存在、说「待接入」的其实已交付、版本号与父 pom 不一致、启动命令指向不存在的模块。
 * 这几类的共同点是：<b>读者不会怀疑文档，只会怀疑自己</b>，排查成本远高于一次断言。
 *
 * <p>全部判据都<b>从真源取证</b>（源码文件名、import、根 pom、模块 pom），不写死名单；
 * 唯一的写死是 JDK 的 {@code java.lang} 类型名与一张带理由的豁免表——前者是语言事实不会漂，
 * 后者是「fail-closed 棘轮」：新增未登记就红，条目不再被引用也红（陈旧的豁免本身就是一种漂移）。
 */
class DocClaimsTest {

    private static final Path ROOT = CatalogIntegrityTest.ROOT;

    /**
     * 文档里出现但不属于本库、也不被任何源码 import 的类型——<b>必须逐条登记理由</b>。
     *
     * <p>这是守卫里唯一的人为名单，但它与「写死名单」相反：名单外的未知名字一律报红（fail-closed），
     * 且条目一旦不再被文档引用也会报红。所以它只会漏掉"有人特意放行"的，不会静默放过新增的。
     */
    private static final Map<String, String> EXTERNAL_TYPE_ALLOWLIST = Map.of(
            "RequestContextFilter", "Boot 内置 Filter 名——这一处在说「别把你的 Bean 叫这个名字」，"
                    + "不是本库类型，也不该被 import"
    );

    /**
     * JDK {@code java.lang} 里的类型名：语言事实，不会漂，允许直接出现在文档里。
     *
     * <p>只收 {@code java.lang} 与最常用的 {@code java.util} 类型——收多了等于把闸门放开，
     * 收少了会逼人给 {@code String} 也登记豁免。
     */
    private static final Set<String> JDK_TYPES = Set.of(
            "String", "Object", "Integer", "Long", "Boolean", "Double", "Float", "Short", "Byte",
            "Character", "Number", "Void", "Class", "Enum", "Record", "Iterable", "Comparable",
            "CharSequence", "StringBuilder", "Thread", "System", "Math", "Exception", "RuntimeException",
            "Error", "Throwable", "Deprecated", "Override",
            "IllegalArgumentException", "IllegalStateException", "NullPointerException",
            "ClassCastException", "UnsupportedOperationException", "NumberFormatException",
            "UnsupportedClassVersionError", "NoSuchMethodError", "Map", "List", "Set", "Optional"
    );

    /** 形如 `Foo` 或 `cn.youhuale.leitu.a.b.Foo`（不含泛型、方法调用、参数）的 code span 才算类型引用。 */
    private static final Pattern TYPE_SPAN =
            Pattern.compile("^(?:[a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)*\\.)?([A-Z][A-Za-z0-9]*)$");

    /**
     * 「待…」与模块名之间允许的最大距离。
     *
     * <p>按整行配对会把不相干的「待」算到模块头上：{@code config-source} 的成套答案段里
     * 既有「引了 starter 就自动装配」，也有段末的「配置中心 adapter 仍待真实痛点接入」——
     * 实测两处相距 366 字符，而真正在说同一件事的那对只相距 28。80 这个阈值要能区分这两者。
     */
    private static final int PENDING_WINDOW = 80;

    /** 「已交付」与模块名之间允许的最大距离（这个短语总是紧挨着模块名写）。 */
    private static final int DONE_WINDOW = 40;

    // ---------------------------------------------------------------- P0-1 签名/类型存在性

    /**
     * 问题页与接入清单里 {@code code span} 中出现的 Java 类型名，必须在仓库源码里真实存在。
     *
     * <p>由来（MATURITY.md P0-1）：{@code what-happened} 曾缺 {@code ObservationSink} 的一行完整签名，
     * 三条独立采样的执行者都为此去读源码，其中一条读了 4.9 万字符——<b>缺一行签名比缺一整页说明更贵</b>：
     * 整页可以不读，类型名错了就写不出能编译的代码，而读者会先怀疑自己的目录结构。
     * 补上签名只解决了那一处；「下一处写错没人发现」这件事当时没有任何守卫。
     *
     * <p>判据三层取证，全部来自实现而不是名单：
     * <ol>
     *   <li>仓库里存在同名 {@code .java}（本库类型）；</li>
     *   <li>或者全仓源码 import 过这个简单名（三方类型——<b>真源是 import</b>，不是我想得起来的名单）；</li>
     *   <li>或者是 JDK {@code java.lang} 类型 / 已登记豁免的外来类型（后者见 {@link #EXTERNAL_TYPE_ALLOWLIST}）。</li>
     * </ol>
     * 三条都不成立 → 红。
     */
    @Test
    void 问题页与接入清单点名的Java类型必须真实存在() throws IOException {
        TypeScan scan = scanDocumentedTypes(ROOT);
        assertTrue(scan.checked() >= 30,
                "只扫到 " + scan.checked() + " 个类型引用——太少了。判据很可能失效（code span 解析式与文档写法脱节），"
                        + "此时本守卫会静默恒绿，比不守更危险");

        assertTrue(scan.problems().isEmpty(),
                "文档点名的 Java 类型在源码里找不到（照它写会编译不过，而读者会先怀疑自己）：\n"
                        + String.join("\n", scan.problems())
                        + "\n修复三选一：① 改成真实存在的类型名；"
                        + "② 若它是三方类型，先让仓库源码真的 import 它（判据取 import 为真源）；"
                        + "③ 若它是本库故意不用的外部类型（如「别叫这个名字」），登记进 EXTERNAL_TYPE_ALLOWLIST 并写明理由。");

        Set<String> stale = new LinkedHashSet<>(EXTERNAL_TYPE_ALLOWLIST.keySet());
        stale.removeAll(scan.allowlistUsed());
        assertTrue(stale.isEmpty(),
                "豁免表里的条目已不再被任何文档引用，却还留着：" + stale
                        + "——陈旧的豁免会让下一个人以为它还在生效，也会掩盖一处新出现的名字漂移。删掉它");
    }

    /**
     * <b>反向自测</b>：文档点名一个不存在的类型，判据必须报红；补上源文件后必须放行。
     *
     * <p>存在性断言最典型的退化是「永远绿」。这里在临时目录里造一个最小仓库，
     * 先确认它真的会红，再确认它真的会绿——两步都过，这条守卫才算装上了。
     */
    @Test
    void 反向自测_文档点名不存在的类型必然报红(@TempDir Path tmp) throws IOException {
        Path configReader = tmp.resolve(
                "leitu-core/src/main/java/cn/youhuale/leitu/core/config/api/ConfigReader.java");
        Files.createDirectories(configReader.getParent());
        Files.writeString(configReader, "package cn.youhuale.leitu.core.config.api;\n");

        Path page = tmp.resolve("docs/problems/neg.md");
        Files.createDirectories(page.getParent());
        Files.writeString(page, """
                # 反向自测占位

                - 用 `ConfigReader` 读配置，用 `PhantomSink` 记观测。
                """);

        TypeScan broken = scanDocumentedTypes(tmp);
        assertTrue(broken.problems().stream().anyMatch(p -> p.contains("PhantomSink")),
                "守卫失效：文档点名了不存在的 PhantomSink，判据却判为通过——症状：" + broken.problems());
        assertTrue(broken.problems().stream().noneMatch(p -> p.contains("ConfigReader")),
                "误报：ConfigReader 真实存在却被判为缺失——症状：" + broken.problems());

        Files.writeString(configReader.getParent().resolve("PhantomSink.java"),
                "package cn.youhuale.leitu.core.config.api;\n");
        TypeScan fixed = scanDocumentedTypes(tmp);
        assertTrue(fixed.problems().isEmpty(),
                "补上源文件后应当放行，实际仍报红：" + fixed.problems());
    }

    /** 扫描结果：判了多少条、哪些不合格、用到了哪些豁免条目。 */
    private record TypeScan(int checked, List<String> problems, Set<String> allowlistUsed) {
    }

    private static TypeScan scanDocumentedTypes(Path root) throws IOException {
        Set<String> own = new LinkedHashSet<>();
        Set<String> foreign = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !f.toString().contains("/target/")).toList()) {
                own.add(p.getFileName().toString().replace(".java", ""));
                for (String line : Files.readString(p).split("\\R")) {
                    Matcher m = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+);").matcher(line);
                    if (m.find()) {
                        String fqn = m.group(1);
                        if (!fqn.startsWith("cn.youhuale.leitu.")) {
                            foreign.add(fqn.substring(fqn.lastIndexOf('.') + 1));
                        }
                    }
                }
            }
        }

        List<String> problems = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        int checked = 0;
        for (Path doc : answerDocs(root)) {
            String text = Files.readString(doc);
            for (Span span : codeSpans(text)) {
                Matcher m = TYPE_SPAN.matcher(span.text());
                if (!m.matches()) {
                    continue;
                }
                checked++;
                String name = m.group(1);
                if (own.contains(name) || foreign.contains(name) || JDK_TYPES.contains(name)) {
                    continue;
                }
                if (EXTERNAL_TYPE_ALLOWLIST.containsKey(name)) {
                    used.add(name);
                    continue;
                }
                problems.add(rel(root, doc) + ":" + span.line() + " 点名 `" + span.text()
                        + "`，但仓库里既没有 " + name + ".java，也没有任何源码 import 过它");
            }
        }
        return new TypeScan(checked, problems, used);
    }

    // ---------------------------------------------------------------- P0-2 成套答案 vs 交付状态

    /**
     * 答案页的「成套答案」段若写「待建/待接入」，而它点名的模块其实已交付，判据必须红。
     *
     * <p>由来（本库已经犯过两次，两次都是靠采样抓到）：{@code config-source} 在 starter 已交付之后
     * 还写着「starter 待接入」，{@code README} 在 springdoc 标了 optional 之后还写着「引依赖即得」。
     * 这类漂移最贵：<b>文档不是漏了一条信息，而是主动把人挡在门外</b>——读者会绕开已经能用的东西自己造。
     *
     * <p>「已交付」的机器判据（不靠人记得）：模块在根 pom 的 {@code <modules>} 里、目录存在、
     * 有 {@code src/main}、且生效版本不含 {@code SNAPSHOT}。
     *
     * <p>反向也判：写着「已交付」而模块并不在 {@code <modules>} 里，同样是谎——两个方向都要能红。
     */
    @Test
    void 答案页的成套答案不得把已交付模块写成待接入() throws IOException {
        DeliveryScan scan = scanDeliveryClaims(ROOT);
        assertTrue(scan.deliveredClaims() >= 1,
                "全仓没有一条「已交付」的成套答案声明——要么答案全退回去了，要么判据的取词方式与文档写法脱节了");
        assertTrue(scan.problems().isEmpty(),
                "答案页对「交付到哪一步」的宣称与实现不符——这是本库认定的最贵红灯（文档主动误导，"
                        + "读者会绕开已经能用的东西自己造）：\n" + String.join("\n", scan.problems()));
    }

    /** <b>反向自测</b>：已交付模块被写成「待建」必须红；把「待」去掉必须绿。 */
    @Test
    void 反向自测_把已交付模块写成待建必然报红(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("pom.xml"), """
                <project>
                  <version>0.2.1</version>
                  <modules><module>leitu-capability-demo</module></modules>
                </project>
                """);
        Path module = tmp.resolve("leitu-capability-demo");
        Files.createDirectories(module.resolve("src/main/java"));
        Files.writeString(module.resolve("pom.xml"),
                "<project><parent><version>0.2.1</version></parent></project>\n");
        Path page = tmp.resolve("docs/problems/neg.md");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "- **成套答案**：`leitu-capability-demo`（待建）\n");

        DeliveryScan broken = scanDeliveryClaims(tmp);
        assertTrue(broken.problems().stream().anyMatch(p -> p.contains("leitu-capability-demo")),
                "守卫失效：模块已交付、文档写「待建」，判据却判为通过——症状：" + broken.problems());

        Files.writeString(page, "- **成套答案**：`leitu-capability-demo`（已交付 0.2.1）\n");
        assertTrue(scanDeliveryClaims(tmp).problems().isEmpty(),
                "把「待建」改成「已交付」后应当放行，实际仍报红：" + scanDeliveryClaims(tmp).problems());

        // 另一个方向：宣称已交付、但模块根本不在 <modules> 里
        Files.writeString(page, "- **成套答案**：`leitu-capability-ghost`（已交付）\n");
        assertTrue(scanDeliveryClaims(tmp).problems().stream()
                        .anyMatch(p -> p.contains("leitu-capability-ghost")),
                "守卫失效：宣称已交付、模块却不在 <modules> 里，判据没红");
    }

    private record DeliveryScan(int deliveredClaims, List<String> problems) {
    }

    private static DeliveryScan scanDeliveryClaims(Path root) throws IOException {
        Path rootPom = root.resolve("pom.xml");
        if (!Files.exists(rootPom)) {
            return new DeliveryScan(0, List.of());
        }
        String pom = Files.readString(rootPom);
        Set<String> modules = new LinkedHashSet<>();
        Matcher m = Pattern.compile("<module>([^<]+)</module>").matcher(pom);
        while (m.find()) {
            modules.add(m.group(1).trim());
        }
        String projectVersion = firstVersion(pom);

        Pattern pending = Pattern.compile("待建|待接|待自建|尚未交付|未交付|待真实痛点");
        Pattern claimed = Pattern.compile("已交付");
        Pattern token = Pattern.compile("`?(leitu-[a-z0-9-]+|starter)`?");

        int deliveredClaims = 0;
        List<String> problems = new ArrayList<>();
        Path problemsDir = root.resolve("docs/problems");
        if (!Files.isDirectory(problemsDir)) {
            return new DeliveryScan(0, List.of());
        }
        try (Stream<Path> pages = Files.list(problemsDir)) {
            for (Path page : pages.filter(p -> p.toString().endsWith(".md")).sorted().toList()) {
                String[] lines = Files.readString(page).split("\\R");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    if (!line.contains("成套答案")) {
                        continue;
                    }
                    Matcher t = token.matcher(line);
                    while (t.find()) {
                        String name = t.group(1);
                        String module = name.equals("starter") ? "leitu-spring-boot-starter" : name;
                        boolean delivered = isDelivered(root, modules, module, projectVersion);
                        // 「待」与「已交付」都要落在模块名附近才算在说这个模块：
                        // config-source 的成套答案段里同时有「引了 starter」与段末的「配置中心 adapter 仍待接入」，
                        // 按整行配对会把一个不相干的「待」算到 starter 头上（实测两处相距 366 字符）。
                        boolean saysPending = near(line, pending, t.start(), PENDING_WINDOW);
                        boolean saysDone = near(line, claimed, t.start(), DONE_WINDOW);
                        if (saysDone && !saysPending) {
                            deliveredClaims++;
                        }
                        if (saysPending && delivered) {
                            problems.add(rel(root, page) + ":" + (i + 1) + " 写「" + module
                                    + " 待接入」，但该模块已在根 pom <modules> 里且版本非 SNAPSHOT（已交付）"
                                    + "——读者会绕开能用的东西自己造");
                        }
                        if (saysDone && !saysPending && !delivered) {
                            problems.add(rel(root, page) + ":" + (i + 1) + " 宣称「" + module
                                    + " 已交付」，但它不在根 pom <modules> 里（或仍为 SNAPSHOT）"
                                    + "——自封的「已完成」比不做更坏");
                        }
                    }
                }
            }
        }
        return new DeliveryScan(deliveredClaims, problems);
    }

    /** 文本里是否存在某模式的匹配，且其起始位置离 {@code at} 不超过 {@code window}。 */
    private static boolean near(String line, Pattern pattern, int at, int window) {
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            if (Math.abs(m.start() - at) <= window) {
                return true;
            }
        }
        return false;
    }

    /** 模块是否已交付：在 {@code <modules>} 里、目录与 src/main 存在、生效版本不含 SNAPSHOT。 */
    private static boolean isDelivered(Path root, Set<String> modules, String module, String projectVersion)
            throws IOException {
        if (!modules.contains(module)) {
            return false;
        }
        Path dir = root.resolve(module);
        if (!Files.isDirectory(dir.resolve("src/main"))) {
            return false;
        }
        Path pom = dir.resolve("pom.xml");
        String version = Files.exists(pom) ? firstVersion(Files.readString(pom)) : projectVersion;
        return !version.isEmpty() && !version.contains("SNAPSHOT");
    }

    // ---------------------------------------------------------------- P1 状态码表

    /**
     * {@code failure-response} 的状态码表必须与 {@code ProblemJson.statusOf} 一致。
     *
     * <p>这张表原先只活在代码里，两轮采样各有执行者靠反编译或实测才确认（2026-10-28 记进文档）。
     * 一旦代码改了分支而表没改，读者拿到的就是一张"看着权威、其实过期"的表——比没有表更坏。
     *
     * <p>判据两侧都从真源取：文档侧取表内三位数，代码侧取 {@code statusOf} 方法体里
     * {@code return N} 与三元表达式两臂的数字（刻意排除 {@code status < 400 || status > 599}
     * 这类范围校验里的数字，它们不是返回值）。
     */
    @Test
    void 状态码表必须与ProblemJson的分支一致() throws IOException {
        Set<String> docCodes = failureStatusCodesInDoc();
        Set<String> codeCodes = failureStatusCodesInCode();
        assertTrue(docCodes.size() >= 2, "文档的状态码表里只解析出 " + docCodes + "——表可能被改写，本守卫会恒绿");
        assertTrue(codeCodes.size() >= 2, "statusOf 里只解析出 " + codeCodes + "——解析式与实现脱节，本守卫会恒绿");
        assertEquals(codeCodes, docCodes,
                "failure-response 的状态码表与 ProblemJson.statusOf 不一致：文档写 " + docCodes
                        + "，代码返回 " + codeCodes
                        + "。这张表是执行者靠反编译才能确认的那类事实，写错会让所有人按错的状态码对接");

        String doc = Files.readString(ROOT.resolve("docs/problems/failure-response.md"));
        assertTrue(doc.contains("guard.denied"),
                "状态码表必须说明 403 来自保留 type guard.denied——否则读者会以为可以自造同名 type 拿到 403");
        assertTrue(Files.readString(ROOT.resolve(
                        "leitu-core/src/main/java/cn/youhuale/leitu/core/failure/model/FailureNotice.java"))
                        .contains("GUARD_TYPE = \"guard.denied\""),
                "文档说的保留 type 是 guard.denied，但 FailureNotice 里没有这个常量——两处对不上了");
    }

    private static Set<String> failureStatusCodesInDoc() throws IOException {
        String doc = Files.readString(ROOT.resolve("docs/problems/failure-response.md"));
        Set<String> codes = new LinkedHashSet<>();
        boolean inSection = false;
        for (String line : doc.split("\\R")) {
            if (line.startsWith("**HTTP 状态码怎么定**")) {
                inSection = true;
                continue;
            }
            if (!inSection) {
                continue;
            }
            if (line.startsWith("**")) {
                break;   // 下一段开始
            }
            Matcher m = Pattern.compile("\\|\\s*(\\d{3})\\s*\\|?\\s*$").matcher(line);
            if (m.find()) {
                codes.add(m.group(1));
            }
        }
        return codes;
    }

    private static Set<String> failureStatusCodesInCode() throws IOException {
        String src = Files.readString(ROOT.resolve(
                "leitu-adapter-web/src/main/java/cn/youhuale/leitu/adapter/web/internal/ProblemJson.java"));
        int start = src.indexOf("static int statusOf(");
        int end = src.indexOf("private static int parseStatus", start);
        assertTrue(start > 0 && end > start, "ProblemJson.statusOf 找不到——守卫的定位式与实现脱节了");
        String body = src.substring(start, end);
        Set<String> codes = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?:return\\s+|\\?\\s*|:\\s*)(\\d{3})\\b").matcher(body);
        while (m.find()) {
            codes.add(m.group(1));
        }
        return codes;
    }

    // ---------------------------------------------------------------- P1 插件版本

    /**
     * 接入清单让读者照抄的三个插件版本，必须等于父 pom 实际托管的版本。
     *
     * <p>由来（2026-10-28）：父 pom 没托管 {@code spring-boot-maven-plugin} 时，{@code <parent>} 姿势下
     * 实测解析到 {@code 4.2.0-RC1}——一次看不见的版本漂移。修法是两边都改：父 pom 托管 + 文档写明版。
     * 但"两边都改"恰恰是最容易只改一半的地方，所以这里把它们对起来。
     *
     * <p>为什么判据要解析 {@code ${property}}：托管版本本来就是属性引用，直接比字符串会永远不等，
     * 逼人把属性写死——那是另一种漂移。
     */
    @Test
    void 接入清单让读者钉的插件版本必须等于父pom托管的版本() throws IOException {
        Map<String, String> docVersions = docPluginVersions();
        assertEquals(3, docVersions.size(),
                "接入清单里应恰好给出三个插件的版本（compiler / surefire / spring-boot），实际解析到 "
                        + docVersions + "——文档改写后本守卫会静默少判，必须一起改");

        Map<String, String> managed = managedPluginVersions();
        for (Map.Entry<String, String> e : docVersions.entrySet()) {
            String artifact = e.getKey();
            assertTrue(managed.containsKey(artifact),
                    "文档让读者钉 " + artifact + " 的版本，而父 pom 的 pluginManagement 里没有它——"
                            + "托管缺失时版本只能碰运气（实测会解析到含 RC 的版本）");
            assertEquals(managed.get(artifact), e.getValue(),
                    "文档写的 " + artifact + " 版本是 " + e.getValue() + "，父 pom 托管的是 "
                            + managed.get(artifact) + "——照文档钉的人会拿到与库自测不同的版本");
        }
    }

    private static Map<String, String> docPluginVersions() throws IOException {
        String doc = Files.readString(ROOT.resolve("docs/INTEGRATION.md"));
        Map<String, String> versions = new LinkedHashMap<>();
        Matcher m = Pattern.compile(
                "`(maven-compiler-plugin|maven-surefire-plugin|spring-boot-maven-plugin)`\\s*(\\d+\\.\\d+\\.\\d+)")
                .matcher(doc);
        while (m.find()) {
            versions.putIfAbsent(m.group(1), m.group(2));
        }
        return versions;
    }

    private static Map<String, String> managedPluginVersions() throws IOException {
        String pom = Files.readString(ROOT.resolve("pom.xml"));
        Map<String, String> properties = new LinkedHashMap<>();
        Matcher p = Pattern.compile("<([\\w.-]+)>([^<${}]+)</\\1>")
                .matcher(regionOf(pom, "<properties>", "</properties>"));
        while (p.find()) {
            properties.put(p.group(1), p.group(2).trim());
        }

        Map<String, String> versions = new LinkedHashMap<>();
        String management = regionOf(pom, "<pluginManagement>", "</pluginManagement>");
        Matcher plugin = Pattern.compile("(?s)<plugin>(.*?)</plugin>").matcher(management);
        while (plugin.find()) {
            String block = plugin.group(1);
            Matcher artifact = Pattern.compile("<artifactId>([^<]+)</artifactId>").matcher(block);
            Matcher version = Pattern.compile("<version>([^<]+)</version>").matcher(block);
            if (!artifact.find() || !version.find()) {
                continue;
            }
            String raw = version.group(1).trim();
            Matcher ref = Pattern.compile("^\\$\\{([\\w.-]+)}$").matcher(raw);
            versions.put(artifact.group(1).trim(), ref.matches() ? properties.getOrDefault(ref.group(1), raw) : raw);
        }
        return versions;
    }

    // ---------------------------------------------------------------- P1 启动命令

    /**
     * 接入清单里的启动命令必须指向真实存在的模块与真实存在的 profile。
     *
     * <p>「文档全篇没有如何启动应用」在 2026-10-26 与 10-28 两轮采样里<b>各登记一次才修</b>。
     * 命令补上了，但它同样是会漂的：模块目录改名、profile 改名，命令就变成一条不可能执行的指引，
     * 而读者会以为是自己环境的问题。
     */
    @Test
    void 接入清单里的启动命令必须指向真实模块与真实profile() throws IOException {
        String doc = Files.readString(ROOT.resolve("docs/INTEGRATION.md"));
        List<String> problems = new ArrayList<>();
        int commands = 0;
        for (String line : doc.split("\\R")) {
            if (!line.contains("spring-boot:run")) {
                continue;
            }
            commands++;
            Matcher m = Pattern.compile("-pl\\s+(\\S+)").matcher(line);
            if (!m.find()) {
                continue;   // 在读者自己的项目里执行，无从校验
            }
            String module = m.group(1);
            Path dir = ROOT.resolve(module);
            if (!Files.isDirectory(dir)) {
                problems.add("启动命令里的模块不存在：" + module);
                continue;
            }
            if (!containsText(dir.resolve("src/main/java"), "@SpringBootApplication")) {
                problems.add("启动命令指向的模块里没有 @SpringBootApplication：" + module);
            }
            Matcher profile = Pattern.compile("-Dspring-boot\\.run\\.profiles=([A-Za-z0-9_-]+)").matcher(line);
            if (profile.find()) {
                Path props = dir.resolve("src/main/resources/application-" + profile.group(1) + ".properties");
                if (!Files.exists(props)) {
                    problems.add("启动命令激活的 profile 没有对应配置：" + module
                            + " 缺 src/main/resources/application-" + profile.group(1) + ".properties");
                }
            }
        }
        assertTrue(commands >= 1,
                "接入清单里一条 spring-boot:run 都没有——执行者只能自己猜怎么起服务，"
                        + "这条在 2026-10-26 与 10-28 两轮采样里各登记过一次");
        assertTrue(problems.isEmpty(),
                "启动命令指向了不存在的东西（照着敲必然失败，而读者会先怀疑自己的环境）：\n"
                        + String.join("\n", problems));
    }

    // ---------------------------------------------------------------- P1 配置源链顺序

    /**
     * 文档写的四层源链顺序，必须与自动装配里的装配顺序一致。
     *
     * <p>这一节曾经整段是错的（只写 {@code standard()} 的三源，而 starter 装配的是四层），
     * 2026-10-28 的执行者<b>靠反编译 jar 才确认</b>。顺序尤其贵：它是"我该把配置写在哪"的唯一依据，
     * 写反了会导致本地 yml 压过配置中心——只在"运维改了配置却不生效"时才暴露。
     *
     * <p>判据取四层的名字在这两处出现的<b>先后次序</b>（不比较文案），任一处顺序变了就红。
     */
    @Test
    void 配置源链的文档顺序必须与自动装配一致() throws IOException {
        List<String> chain = List.of("spring-overrides", "ConfigSource", "spring-files", "leitu.properties");

        String page = Files.readString(ROOT.resolve("docs/problems/config-source.md"));
        String answerLine = page.lines()
                .filter(l -> l.contains("成套答案") && l.contains("spring-overrides"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "config-source 的成套答案段里找不到源链——要么被删了，要么改写成了别的形态，本守卫会恒绿"));
        assertInOrder(answerLine, chain, "docs/problems/config-source.md 的成套答案段");

        String integration = regionOf(Files.readString(ROOT.resolve("docs/INTEGRATION.md")),
                "### 引了 starter 之后", "读时求值；秘密");
        assertInOrder(integration, chain, "docs/INTEGRATION.md §2 的源链表");

        String code = Files.readString(ROOT.resolve(
                "leitu-spring-boot-starter/src/main/java/cn/youhuale/leitu/starter/LeituConfigAutoConfiguration.java"));
        assertInOrder(code, List.of("overrides(", "orderedStream", ".files(", "\"leitu.properties\""),
                "LeituConfigAutoConfiguration 的装配顺序");
    }

    // ---------------------------------------------------------------- 工具

    /** 一个 code span 及其所在行号。 */
    private record Span(String text, int line) {
    }

    /** 取正文里的行内 code span（跳过 ``` 围栏块——那里是示例代码，不是"点名"）。 */
    private static List<Span> codeSpans(String text) {
        List<Span> spans = new ArrayList<>();
        boolean inFence = false;
        int line = 0;
        for (String raw : text.split("\\R")) {
            line++;
            if (raw.stripLeading().startsWith("```")) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            Matcher m = Pattern.compile("`([^`]+)`").matcher(raw);
            while (m.find()) {
                spans.add(new Span(m.group(1).strip(), line));
            }
        }
        return spans;
    }

    /** 答案页与接入清单：给 AI 抄的那两处。 */
    private static List<Path> answerDocs(Path root) throws IOException {
        List<Path> docs = new ArrayList<>();
        Path problems = root.resolve("docs/problems");
        if (Files.isDirectory(problems)) {
            try (Stream<Path> listed = Files.list(problems)) {
                docs.addAll(listed.filter(p -> p.toString().endsWith(".md")).sorted().toList());
            }
        }
        Path integration = root.resolve("docs/INTEGRATION.md");
        if (Files.exists(integration)) {
            docs.add(integration);
        }
        return docs;
    }

    /** 断言一组标记在文本里按给定次序出现；缺任何一个都算失败（缺失时顺序无从谈起）。 */
    private static void assertInOrder(String text, List<String> markers, String where) {
        int cursor = -1;
        for (String marker : markers) {
            int at = text.indexOf(marker, cursor + 1);
            assertTrue(at >= 0,
                    where + " 里找不到「" + marker + "」——源链的某一层消失了，或叫法变了；"
                            + "读者会按一条不完整的链去决定配置写在哪");
            assertTrue(at > cursor, where + " 的顺序错了：期望 " + markers + " 依次出现，而「" + marker
                    + "」出现在了更前面——顺序是「配置该写在哪」的唯一依据");
            cursor = at;
        }
    }

    /** 取两个标记之间的文本（含起点、不含终点）。 */
    private static String regionOf(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        assertTrue(start >= 0, "找不到标记 " + startMarker + "——文档结构变了，守卫会恒绿");
        int end = text.indexOf(endMarker, start + startMarker.length());
        return end > start ? text.substring(start, end) : text.substring(start);
    }

    private static String firstVersion(String pom) {
        Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(pom);
        return m.find() ? m.group(1).trim() : "";
    }

    private static boolean containsText(Path dir, String text) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .anyMatch(p -> {
                        try {
                            return Files.readString(p).contains(text);
                        } catch (IOException e) {
                            return false;
                        }
                    });
        }
    }

    private static String rel(Path root, Path file) {
        Path normalized = root.toAbsolutePath().normalize();
        return normalized.relativize(file.toAbsolutePath().normalize()).toString();
    }
}
