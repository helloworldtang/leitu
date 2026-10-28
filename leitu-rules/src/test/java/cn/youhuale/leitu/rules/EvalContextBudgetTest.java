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

/**
 * eval 任务卡的「最小上下文预算」——把「读多少」从目标变成可被机器守住的承诺（ADR-004）。
 *
 * <p>由来（2026-10-28）：任务 03 首采时第一次测了按序读的真实成本——<b>6.2 万字符 / 10 处</b>。
 * 当时拿它对照的目标是「≤5 文件」，而这条目标本身是错的口径：
 * <ul>
 *   <li>答案本来就<b>分页</b>（一条答案一页问题页）是正确设计，把成本记成「文件数」等于惩罚分页；</li>
 *   <li>更糟的是它给的是<b>反向激励</b>：想降低「文件数」，最省事的动作是把答案塞回一个大页面——
 *       那正是本项目立意要消灭的东西；</li>
 *   <li>真正决定成本的是<b>字符数</b>（token 预算），而字符数完全可算、可守、可回归。</li>
 * </ul>
 * 所以这里废弃「≤5 文件」，改成两件机器能判的事：
 * <ol>
 *   <li><b>必须点名到文件</b>：按序读的编号项里出现目录即红——「整目录」等于「你自己决定读多少」，
 *       成本不可测；要范围就用 {@code /**} 显式限定（并且只在该范围真的都是必读时才用）；</li>
 *   <li><b>必须自报预算且不得超</b>：卡上声明「上下文预算 ≤ X 万字符」，编号项的全文合计不得超它，
 *       且任何卡的上界不得超过 {@link #HARD_CAP_WAN} 万——预算随手调大的话，守卫就退化成装饰。</li>
 * </ol>
 *
 * <p><b>计价口径是上界</b>：问题页按<b>全文</b>计，即使卡上写着「只读机制与边界两段」——
 * 上界口径只会高估、不会低估，且与首采的 6.2 万字符同口径，读数才能比。
 *
 * <p><b>为什么只算编号项</b>：编号 = 必读面；「需要查状态与边界时再看 catalog / GLOSSARY」这类
 * 条件阅读故意不编号、不计价——把它算进必读，会让每张卡的预算虚胖 1.6 万字符，反而失去约束力。
 */
class EvalContextBudgetTest {

    private static final Path ROOT = CatalogIntegrityTest.ROOT;

    /**
     * 编号项里的反引号仓库路径。只认这几类前缀：`pom.xml`、`fx.rate.USD-CNY` 这类散文里的碎片
     * 不是文件路径，不该被拉进计价（误伤会让守卫变成噪声，人就开始绕开它）。
     */
    private static final Pattern REPO_PATH =
            Pattern.compile("`((?:docs|examples|catalog|skills|scripts|tools|leitu-[a-z-]+)/[^`\\s]*)`");

    /**
     * 卡上自己声明的预算，单位「万字符」——与 baselines 的读数同口径，免得换算出错。
     *
     * <p>判据<b>锚到行首的「上下文预算：」</b>，不是搜全卡的任意一处：卡的版本说明里也会出现
     * 「（≤ 4.8 万字符，由守卫守）」这类句子，用裸 {@code find()} 会先抓到那一处——
     * 于是改卡内真正的声明不生效，守卫对着旧数字恒绿（第一次自测就踩到了这个坑）。
     */
    private static final Pattern BUDGET = Pattern.compile("(?m)^上下文预算：\\s*[^\\n]*?≤\\s*([\\d.]+)\\s*万字符");

    /** 单卡硬上限：4.8 万字符左右的卡已覆盖「接入清单 + 6 个问题页 + 4 个金样本」的完整必读面。 */
    private static final double HARD_CAP_WAN = 5.0;

    @Test
    void 任务卡的按序读必须点名到文件且不超自报预算() throws IOException {
        List<String> broken = new ArrayList<>();
        int cardsChecked = 0;
        int pathsChecked = 0;

        List<Path> cards;
        try (Stream<Path> listed = Files.list(ROOT.resolve("eval/tasks"))) {
            cards = listed.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }

        for (Path card : cards) {
            String text = read(card);
            String section = orderedReadSection(text);
            if (section == null) {
                continue;   // 对照卡（不使用本库）没有「按序读」段，不属于本守卫的资产面
            }

            Matcher b = BUDGET.matcher(text);
            if (!b.find()) {
                broken.add(rel(card) + " 没声明「上下文预算 ≤ X 万字符」——没有预算的阅读清单等于"
                        + "「读多少看自觉」，实测过一次（6.2 万字符）之后就再没有第二个数字了");
                continue;
            }
            double budget = Double.parseDouble(b.group(1));
            if (budget > HARD_CAP_WAN) {
                broken.add(rel(card) + " 把预算声明成 " + b.group(1) + " 万字符，超过硬上限 " + HARD_CAP_WAN
                        + " 万——预算能随手调大就不是约束，是许愿");
                continue;
            }
            cardsChecked++;

            long chars = 0;
            for (String item : numberedItems(section)) {
                Matcher m = REPO_PATH.matcher(item);
                while (m.find()) {
                    String token = m.group(1);
                    Path target = ROOT.resolve(token.replace("/**", ""));
                    if (!Files.exists(target)) {
                        broken.add(rel(card) + " 点名了不存在的路径：" + token
                                + "——按序读是规格，路径死了执行者只能猜（或违规去翻别处）");
                        continue;
                    }
                    pathsChecked++;
                    if (Files.isDirectory(target) && !token.endsWith("/**")) {
                        broken.add(rel(card) + " 用整目录点名：" + token
                                + "——目录的意思是「你自己决定读多少个文件」，成本既不可测也不可守；"
                                + "改成点名到文件，或用 " + token + "/** 显式限定范围");
                        continue;
                    }
                    chars += charsOf(target);
                }
            }

            long limit = Math.round(budget * 10000);
            if (chars > limit) {
                broken.add(rel(card) + " 的按序读实测 " + chars + " 字符，超出自报预算 " + limit
                        + "（" + b.group(1) + " 万）——超了只有两条路：收窄必读面，或改预算并在卡的版本说明里"
                        + "写清为什么多读的这部分值得（不许默默调大）");
            }
        }

        assertTrue(cardsChecked >= 3,
                "只检查到 " + cardsChecked + " 张含「按序读」的任务卡——采样卡至少 3 张，"
                        + "要么「## 按序读」标题被改了（本守卫的正则随之失效），要么卡被删了；"
                        + "两种都必须处理，不能让守卫静默变成恒绿");
        assertTrue(pathsChecked >= 10,
                "只解析到 " + pathsChecked + " 条仓库路径——取路径的正则很可能与卡的写法脱节了"
                        + "（例如路径换了前缀、反引号改成加粗），届时本守卫会「零路径通过」，比不守更危险");
        assertTrue(broken.isEmpty(), "任务卡的阅读面与预算对不上：\n" + String.join("\n", broken));
    }

    /** 取「## 按序读」段（到下一个二级标题为止）；没有该段返回 null。 */
    private static String orderedReadSection(String text) {
        int start = text.indexOf("\n## 按序读");
        if (start < 0) {
            return null;
        }
        int from = start + 1;
        int end = text.indexOf("\n## ", from + 1);
        return end < 0 ? text.substring(from) : text.substring(from, end);
    }

    /**
     * 段内的编号项（编号行 + 其后的续行，直到空行或下一个编号）。
     *
     * <p>只认编号项：条件阅读（「需要查状态时再看…」）刻意不编号，也就不计入预算。
     */
    private static List<String> numberedItems(String section) {
        List<String> items = new ArrayList<>();
        StringBuilder current = null;
        for (String line : section.split("\\R")) {
            if (line.matches("^\\d+\\.\\s.*")) {
                if (current != null) {
                    items.add(current.toString());
                }
                current = new StringBuilder(line);
            } else if (current != null) {
                if (line.isBlank()) {
                    items.add(current.toString());
                    current = null;
                } else {
                    current.append(' ').append(line.trim());
                }
            }
        }
        if (current != null) {
            items.add(current.toString());
        }
        return items;
    }

    /** 字符数：文件取其长度；`/**` 展开成目录下全部文件（跳过 build 产物）。 */
    private static long charsOf(Path target) throws IOException {
        if (Files.isRegularFile(target)) {
            return read(target).length();
        }
        try (Stream<Path> files = Files.walk(target)) {
            long total = 0;
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                if (f.toString().contains("/target/")) {
                    continue;
                }
                total += read(f).length();
            }
            return total;
        }
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file);
    }

    private static String rel(Path file) {
        return ROOT.relativize(file).toString();
    }
}
