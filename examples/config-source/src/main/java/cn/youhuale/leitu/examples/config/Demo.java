package cn.youhuale.leitu.examples.config;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;

import java.time.Duration;
import java.util.Map;

/**
 * 金样本：配置源的最小可运行示范。
 *
 * <p>照着这个样子读你的配置——这是"配置值从哪来"的标准答案用法
 * （docs/problems/config-source.md；与判定链、观测组合使用）。
 */
public final class Demo {

    public static void main(String[] args) {
        ConfigReader config = ConfigReader.standard();

        // 1) 三源优先级：系统属性盖过 classpath；移除后回到文件值（读时求值）
        System.out.println("文件值:   " + config.get("leitu.golden.greeting", "默认"));
        System.setProperty("leitu.golden.greeting", "from-sysprop");
        try {
            System.out.println("覆盖后:   " + config.get("leitu.golden.greeting", "默认"));
        } finally {
            System.clearProperty("leitu.golden.greeting");
        }
        System.out.println("恢复后:   " + config.get("leitu.golden.greeting", "默认"));

        // 2) 类型化读取带默认值：命中源值与缺失走默认各一次
        System.out.println("pool:     " + config.getInt("leitu.golden.pool", 10));
        System.out.println("timeout:  " + config.getDuration("leitu.golden.timeout", Duration.ofSeconds(30)));
        System.out.println("缺失默认: " + config.getInt("leitu.golden.no-such", 10));

        // 3) 缺失的诚实通道：Optional 版表达「没有默认」
        System.out.println("缺失:     " + config.get("leitu.golden.no-such"));

        // 4) 有值但解析失败 = 错误即教程（消息含 key/源名/格式/示例，永不携带值）
        try {
            config.getInt("leitu.golden.bad-int", 3);
        } catch (IllegalArgumentException e) {
            System.out.println("教学异常: " + e.getMessage());
        }

        // 5) SPI 插拔 + 跨答案组合：Map 源前置覆盖；配置驱动判定链与观测
        ConfigReader withOverride = ConfigReader.of(
                ConfigSources.fromMap("演示覆盖源", Map.of("leitu.golden.budget", "2")),
                ConfigSources.systemProperties(),
                ConfigSources.environmentVariables(),
                ConfigSources.classpathProperties("leitu.properties"));
        int budget = withOverride.getInt("leitu.golden.budget", 100);
        System.out.println("全链:     " + withOverride);
        System.out.println("预算阈值: " + budget + "（来自前置 Map 源——配置中心 adapter 同款插法）");

        var binder = ExecutionContextBinders.threadLocal();
        ExecutionContextReader who = ExecutionContextReader.threadLocal();
        ObservationRecorder recorder = ObservationRecorder.of(event -> System.out.println("观测:     " + event));
        GuardChain chain = GuardChain.of(new CountingGuard(budget));
        try (var scope = binder.bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "trace-200"))) {
            for (int i = 1; i <= 3; i++) {
                Decision d = chain.check(AccessRequest.inbound(
                        who.current().operator().subject(), "ai:invoke", "-"));
                recorder.record(ObservationEvent.of("guard.decision", who.current(),
                        d.isDeny() ? Outcome.failure(d.reason()) : Outcome.success()));
            }
        }
    }

    /** 示例 Guard：每分钟最多放行 N 次（N 来自配置——配置驱动判定的最小示范）。 */
    static final class CountingGuard implements cn.youhuale.leitu.core.guard.spi.Guard {

        private final int limit;

        private int used;

        CountingGuard(int limit) {
            this.limit = limit;
        }

        @Override
        public Decision check(AccessRequest request) {
            if (used >= limit) {
                return Decision.deny("预算耗尽（限 " + limit + " 次）", Retry.later(Duration.ofSeconds(60)));
            }
            used++;
            return Decision.allow();
        }
    }
}
