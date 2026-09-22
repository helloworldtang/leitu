package cn.youhuale.leitu.capability.cache.api;

/**
 * 装载图成环——值依赖它自己，无解，因此<b>大声失败而不是挂死</b>。
 *
 * <p>典型形态：缓存 A 的装载器读了缓存 B，而 B 的装载器又回头读 A 同一个键。
 * 此时两个装载互相等待，谁都算不出值——这不是"慢"，是<b>没有答案</b>，
 * 所以框架不等待、不超时、不静默重试，而是把环路径原样报出来。
 *
 * <p>为什么必须侦测：装载在调用方线程上执行，一旦成环就是线程互等，
 * 症状是"接口卡住、CPU 为零、日志全无"，排查成本极高。
 * 报出环路径后，成因与责任方一眼可见——它在装载器里，不在缓存里。
 */
public class CyclicCacheLoadException extends IllegalStateException {

    private final String cyclePath;

    /**
     * @param cyclePath 环路径（{@code 缓存标签{租户 / 键} → … → 起点}），用于定位是哪个装载器回头读了谁。
     */
    public CyclicCacheLoadException(String cyclePath) {
        super("装载图成环（值依赖它自己，无解——不是慢，是没有答案）：" + cyclePath + "。"
                + System.lineSeparator()
                + "成因：某个装载器直接或间接地读取了它自己正在装载的键——"
                + "缓存只能保证「同一个值只算一次」，不能替你把环解开。"
                + System.lineSeparator()
                + "解法：把被依赖的那层值先算好（put 进缓存，或作为参数传入装载器），"
                + "让装载器不再回头读自己；若这是两个域的双向依赖，通常意味着该值不该由缓存装载器现算。");
        this.cyclePath = cyclePath;
    }

    /** 环路径（机器可读部分，便于日志检索与测试断言）。 */
    public String cyclePath() {
        return cyclePath;
    }
}
