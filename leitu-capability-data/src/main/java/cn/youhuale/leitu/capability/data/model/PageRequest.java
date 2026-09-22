package cn.youhuale.leitu.capability.data.model;

import java.util.Objects;

/**
 * 翻页请求：从第 {@code offset} 行开始，最多取 {@code limit} 行。
 *
 * <p><b>为什么必须有它</b>：{@code findAll()} 是"当前租户全部行"——租户数据在增长，
 * 全量列表迟早变成一次把整张表拖进内存的调用（慢查询 + OOM）。分页不是优化，而是<b>默认用法</b>：
 * 列表接口一律走 {@code findAll(PageRequest)}，全量只留给离线任务与明确知道上限的场合。
 *
 * <p><b>排序是本合同的另一半</b>：没有确定顺序的翻页，第二页可能与第一页重行、也可能漏行。
 * 实现必须给出确定排序（JDBC 用 {@code ORDER BY id}，内存用主键字符串升序），
 * 因此相邻窗口连续翻完 = 每行恰好一次（测试断言的就是这一条）。
 *
 * <p>{@code limit} 的上限不在此处规定——不同表、不同租户规模该有不同的闸门，
 * 由应用侧的配额策略（判定链 {@code BudgetGuard}）来卡，不塞进这块值对象里。
 *
 * @param offset 跳过前多少行（0 = 从头）
 * @param limit  本页最多多少行（必须 ≥ 1；"取全部"请明说上限，不设 magic number）
 */
public record PageRequest(int offset, int limit) {

    public PageRequest {
        if (offset < 0) {
            throw new IllegalArgumentException("offset 不能为负：跳过行数从 0 起算，当前 " + offset);
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须 ≥ 1：本页最多取几行要说清楚，当前 " + limit
                    + "；想要全量请用 findAll() 并确认上限可控（租户规模会增长）");
        }
    }

    /** 第一页：取前 limit 行。 */
    public static PageRequest first(int limit) {
        return new PageRequest(0, limit);
    }

    /** 指定起点与本页上限。 */
    public static PageRequest of(int offset, int limit) {
        return new PageRequest(offset, limit);
    }

    /** 下一页（同一 limit）——连续翻页时用它，不必自己算 offset。 */
    public PageRequest next() {
        Objects.requireNonNull(this, "page 必填");
        return new PageRequest(offset + limit, limit);
    }

    @Override
    public String toString() {
        return "PageRequest{offset=" + offset + ", limit=" + limit + "}";
    }
}
