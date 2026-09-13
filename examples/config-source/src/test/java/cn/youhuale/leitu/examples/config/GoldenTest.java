package cn.youhuale.leitu.examples.config;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.config.spi.ConfigSource;
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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 金测试：锁定配置源的标准用法语义。core 的任何改动让这里变红，即破坏了既有答案。 */
class GoldenTest {

    @Test
    void 三源优先级_系统属性盖过classpath_移除后回落文件值() {
        ConfigReader config = ConfigReader.standard();
        assertEquals("from-classpath", config.get("leitu.golden.greeting", "默认"));
        System.setProperty("leitu.golden.greeting", "from-sysprop");
        try {
            assertEquals("from-sysprop", config.get("leitu.golden.greeting", "默认"));
        } finally {
            System.clearProperty("leitu.golden.greeting");
        }
        assertEquals("from-classpath", config.get("leitu.golden.greeting", "默认"),
                "读时求值：覆盖移除后回到文件值");
    }

    @Test
    void 环境变量盖过classpath() {
        ConfigSource env = ConfigSources.environmentVariables(k ->
                "LEITU_GOLDEN_GREETING".equals(k) ? "from-env" : null);
        ConfigReader config = ConfigReader.of(env, ConfigSources.classpathProperties("leitu.properties"));
        assertEquals("from-env", config.get("leitu.golden.greeting", "默认"));
    }

    @Test
    void 类型化读取_缺失走默认_命中走源值() {
        ConfigReader config = ConfigReader.standard();
        assertEquals(5, config.getInt("leitu.golden.pool", 10));
        assertEquals(Duration.ofSeconds(2), config.getDuration("leitu.golden.timeout", Duration.ofSeconds(30)));
        assertEquals(10, config.getInt("leitu.golden.no-such", 10));
        assertEquals(Optional.empty(), config.get("leitu.golden.no-such"));
    }

    @Test
    void 出现但解析失败_教学异常含key源名示例且不含值() {
        ConfigReader config = ConfigReader.standard();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.getInt("leitu.golden.bad-int", 3));
        String msg = e.getMessage();
        assertTrue(msg.contains("leitu.golden.bad-int"), "含 key：" + msg);
        assertTrue(msg.contains("classpath:leitu.properties"), "含源名：" + msg);
        assertTrue(msg.contains("42"), "含示例：" + msg);
        assertFalse(msg.contains("abc"), "教学异常永不携带值：" + msg);
    }

    @Test
    void 优先级等于组合顺序_toString印全链() {
        ConfigReader config = ConfigReader.of(
                ConfigSources.fromMap("覆盖源", Map.of()),
                ConfigSources.systemProperties(),
                ConfigSources.environmentVariables(),
                ConfigSources.classpathProperties("leitu.properties"));
        String text = config.toString();
        assertTrue(text.contains("覆盖源 → 系统属性 → 环境变量 → classpath:leitu.properties"),
                "全链一处可见：" + text);
    }

    @Test
    void SPI插拔与既有答案组合_fromMap驱动判定与观测() {
        ConfigReader config = ConfigReader.of(
                ConfigSources.fromMap("预算配置", Map.of("leitu.golden.budget", "1")));
        int budget = config.getInt("leitu.golden.budget", 100);

        List<ObservationEvent> events = new ArrayList<>();
        ObservationRecorder recorder = ObservationRecorder.of(events::add);
        GuardChain chain = GuardChain.of(req -> {
            // 配置驱动判定：预算 1 次，第二次否决——业务面携带配置所得值（值静默只约束诊断面）
            if (req.action().endsWith("first")) {
                return Decision.allow();
            }
            return budget >= 2
                    ? Decision.allow()
                    : Decision.deny("预算耗尽（限 " + budget + " 次）", Retry.later(Duration.ofSeconds(60)));
        });

        var binder = ExecutionContextBinders.threadLocal();
        ExecutionContextReader who = ExecutionContextReader.threadLocal();
        try (var scope = binder.bind(ExecutionContext.of(Operator.agent("bot-7", "tenant-a"), "trace-200"))) {
            Decision d = chain.check(AccessRequest.inbound(who.current().operator().subject(), "ai:invoke", "-"));
            recorder.record(ObservationEvent.of("guard.decision", who.current(),
                    d.isDeny() ? Outcome.failure(d.reason()) : Outcome.success()));
        }
        assertEquals(1, events.size());
        assertInstanceOf(Outcome.Failure.class, events.get(0).outcome());
        assertTrue(((Outcome.Failure) events.get(0).outcome()).reason().contains("预算耗尽"));
    }

    @Test
    void 环境变量宽松归一_注入式环境源() {
        ConfigSource env = ConfigSources.environmentVariables(k ->
                "LEITU_GOLDEN_GREETING".equals(k) ? "from-relaxed" : null);
        ConfigReader config = ConfigReader.of(env);
        assertEquals("from-relaxed", config.get("leitu.golden.greeting", "默认"),
                "datasource.url 形式的点分键应命中 DATASOURCE_URL 归一形");
    }
}
