import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 新答案起步器——把「加一条答案要动约 10 个同步点」从靠人记得变成机械清单。
 *
 * <p>用法（仓库根目录，JDK 21 直接运行，无其他依赖）：
 * <pre>java scripts/NewAnswer.java &lt;id&gt; "问题一句话" [--facet pipeline|capability] [--required always|conditional]</pre>
 *
 * <p>做什么：
 * <ol>
 *   <li>生成 {@code docs/problems/<id>.md} 四段式骨架（合法链接、待填标记）；</li>
 *   <li>打印 catalog 立目 JSON 片段（只打印不写入——目录是机器地图，schema 变更须过人）；</li>
 *   <li>打印同步点清单：每一处要动的地方、路径、以及是否有守卫兜底。</li>
 * </ol>
 *
 * <p>不做什么：不改 catalog、不改根 pom、不生成金样本代码——那些要么需要 schema 级决策，
 * 要么需要真实实现，机械生成只会生产「看起来完成了」的假答案。
 *
 * <p>为什么有这个脚本（2026-10-26 体检 · 维度 7）：实测加一条答案要动 24 个文件、
 * 其中约 10 个是同步点；守门人只保证「改了要一致」，不保证「不用改」——
 * 本脚本把「要改哪些」从记忆变成输出。
 */
public class NewAnswer {

    public static void main(String[] args) throws IOException {
        Path root = Paths.get("").toAbsolutePath();
        if (!Files.exists(root.resolve("catalog/problems.json"))) {
            System.err.println("请在仓库根目录运行：找不到 catalog/problems.json");
            System.exit(1);
        }
        List<String> rest = new ArrayList<>();
        String facet = "pipeline";
        String required = "conditional";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--facet" -> facet = args[++i];
                case "--required" -> required = args[++i];
                default -> rest.add(args[i]);
            }
        }
        if (!List.of("pipeline", "capability").contains(facet)
                || !List.of("always", "conditional").contains(required)) {
            System.err.println("--facet 只能是 pipeline|capability；--required 只能是 always|conditional");
            System.exit(1);
        }
        if (rest.size() < 2) {
            System.err.println("用法：java scripts/NewAnswer.java <id> \"问题一句话\" [--facet pipeline|capability] [--required always|conditional]");
            System.exit(1);
        }
        String id = rest.get(0);
        String problem = rest.get(1);
        if (!id.matches("[a-z][a-z0-9-]*")) {
            System.err.println("id 必须是小写字母开头的 kebab-case（唯一指称第一关）： " + id);
            System.exit(1);
        }

        Path doc = root.resolve("docs/problems/" + id + ".md");
        Path catalog = root.resolve("catalog/problems.json");
        if (Files.exists(doc)) {
            System.err.println("已存在 " + doc + "——拒绝覆盖（答案页是资产，不是产物）");
            System.exit(1);
        }
        if (Files.readString(catalog).contains("\"id\": \"" + id + "\"")) {
            System.err.println("catalog 里已有条目 " + id + "——拒绝重复立目");
            System.exit(1);
        }

        Files.writeString(doc, skeleton(id, problem, LocalDate.now().toString()));
        System.out.println("✓ 已生成 " + root.relativize(doc));
        System.out.println();
        System.out.println("== catalog 立目片段（自行粘进 entries，保持字母序就近） ==");
        System.out.println(catalogSnippet(id, problem, facet, required));
        System.out.println();
        System.out.println("== 同步点清单（✓=有守卫兜底，漏了会红；□=靠人，漏了不红） ==");
        checklist(id).forEach(item -> System.out.println(item));
        System.out.println();
        System.out.println("== 收尾命令 ==");
        System.out.println("  java scripts/ExportArchitecture.java        # catalog 变更后再生成 4R 视图");
        System.out.println("  cp catalog/problems.json tools/cli/catalog-snapshot.json   # npm 快照同步");
        System.out.println("  node tools/cli/check-skill.mjs              # SKILL 路由守门");
        System.out.println("  ./mvnw verify                               # 唯一构建入口");
    }

    /** 四段式骨架：标题与段落齐全（四段式守卫对 answered 才生效，但骨架一次到位）。 */
    private static String skeleton(String id, String problem, String date) {
        return """
                # %s

                > **%s**
                > 目录条目 `%s`；状态变迁 open → drafted → answered 由五件套齐缺决定（ADR-006）。
                > 本骨架由 `scripts/NewAnswer.java` 生成于 %s——填完各段后删除本提示。

                ## 一、为什么

                <!-- 这个问题为什么反复出现：场景枚举（至少三类真实场景）+ 不解决的成本（漂移长什么样）。 -->

                ## 二、机制（标准答案的形状）

                <!-- 标准答案长什么样：接口 / 合同 / 默认实现。只写形状，不写教程；教程归金样本。 -->

                ## 三、取舍

                <!-- 每个关键决定付出了什么代价、排除了哪些同样合理的方案、为什么这样排。 -->

                ## 四、边界

                <!-- 到哪儿为止：不承诺什么、什么条件下答案失效、缝合给谁（留缝声明）。 -->
                """.formatted(id, problem, id, date);
    }

    /** catalog 片段：status=open 起步，answered 时由五件套齐缺驱动（人改状态，机器验存在）。 */
    private static String catalogSnippet(String id, String problem, String facet, String required) {
        return """
                {
                  "id": "%s",
                  "problem": "%s",
                  "facet": "%s",
                  "required": "%s",
                  "status": "open",
                  "stability": null,
                  "since": null,
                  "replaced_by": null,
                  "depends_on": [],
                  "links": {
                    "doc": "docs/problems/%s.md",
                    "code": null,
                    "rule": null,
                    "example": null
                  }
                },""".formatted(id, problem, facet, required, id);
    }

    /** 同步点清单：✓ 有守卫（漏了构建红），□ 靠人（漏了不红，靠评审）。 */
    private static List<String> checklist(String id) {
        return List.of(
                "✓  docs/problems/" + id + ".md 四段式填完，相对链接有效（CatalogIntegrityTest：四段式 + 链接）",
                "✓  catalog 立目（枚举合法 / id 唯一 / depends_on 存在——CatalogIntegrityTest）",
                "✓  answered 时五件套真实存在：code / rule / example / doc / 本条目（CatalogIntegrityTest）",
                "✓  SKILL.md 路由表覆盖全部 answered（check-skill 第二断言）",
                "✓  catalog ↔ npm 快照一致（check-skill 第三断言：cp 后跑一次）",
                "✓  views.md 再生成（CI staleness 检查：java scripts/ExportArchitecture.java）",
                "✓  全仓 markdown 链接有效（CatalogIntegrityTest：改路径必须同提交改引用）",
                "□  金样本 examples/" + id + "/（answered 必填）：**记得在根 pom 的 eval profile 与 modules 登记**",
                "□  守护规则：core 边界断言或 rules 断言——答案必须有一条机器守卫，没有就不许 answered",
                "□  SKILL.md 路由行触发场景要按「用户想做什么」写，不是按实现名写",
                "□  新术语过三关（唯一指称 / 业界对齐 / 不带兑现不了的承诺）后进 GLOSSARY",
                "□  设计有先例价值时写 ADR 并更新 docs/decisions/README 索引（范围守卫会查编号）",
                "□  README「已交付的答案」表加行（answered 时；open 不进表）",
                "□  值得成为标准任务时进 eval/tasks/（不阻塞 answered，阻塞「已验证」的宣称）");
    }
}
