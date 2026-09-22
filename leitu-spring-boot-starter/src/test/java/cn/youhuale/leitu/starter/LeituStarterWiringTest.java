package cn.youhuale.leitu.starter;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.config.spi.ConfigSource;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import cn.youhuale.leitu.core.observe.model.Outcome;
import cn.youhuale.leitu.core.observe.spi.ObservationSink;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配金测试：锁定 starter 的默认装配语义与「用户 Bean 优先」契约。
 * core 或 starter 的任何改动让这里变红，即破坏了既有装配答案（五件套之金样本）。
 */
class LeituStarterWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LeituContextAutoConfiguration.class,
                    LeituGuardAutoConfiguration.class,
                    LeituObserveAutoConfiguration.class,
                    LeituConfigAutoConfiguration.class));

    @Test
    void 默认装配_四个端口全部就位() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ExecutionContextReader.class);
            assertThat(ctx).hasSingleBean(GuardChain.class);
            assertThat(ctx).hasSingleBean(ObservationRecorder.class);
            assertThat(ctx).hasSingleBean(ConfigReader.class);
        });
    }

    @Test
    void 空判定链_安全默认拒绝() {
        runner.run(ctx -> {
            Decision d = ctx.getBean(GuardChain.class)
                    .check(AccessRequest.inbound("alice", "order:query", "-"));
            assertThat(d.isDeny()).as("空链 = deny-by-default（分维度默认值）").isTrue();
        });
    }

    @Test
    void 应用声明的Guard被收编进判定链() {
        Guard denyAll = request -> Decision.deny("装配测试否决", Retry.never());
        runner.withBean("demoGuard", Guard.class, () -> denyAll).run(ctx -> {
            Decision d = ctx.getBean(GuardChain.class)
                    .check(AccessRequest.inbound("alice", "order:query", "-"));
            assertThat(d.isDeny()).isTrue();
            assertThat(d.reason()).contains("装配测试否决");
        });
    }

    @Test
    void 用户Bean优先_自定义读取器不被覆盖() {
        ExecutionContextReader custom = () -> ExecutionContext.of(Operator.system(), "t-custom");
        runner.withBean(ExecutionContextReader.class, () -> custom).run(ctx ->
                assertThat(ctx.getBean(ExecutionContextReader.class)).isSameAs(custom));
    }

    @Test
    void 观测落点被收编_一次记录扇出全部落点() {
        List<ObservationEvent> received = new ArrayList<>();
        runner.withBean("testSink", ObservationSink.class, () -> received::add).run(ctx -> {
            ctx.getBean(ObservationRecorder.class).record(ObservationEvent.of(
                    "starter.test", ExecutionContext.of(Operator.system(), "t-1"),
                    Outcome.success(), Duration.ofMillis(1)));
            assertThat(received).hasSize(1);
        });
    }

    @Test
    void 无落点_日志级默认_记录不抛异常() {
        runner.run(ctx -> ctx.getBean(ObservationRecorder.class).record(ObservationEvent.of(
                "starter.test", ExecutionContext.of(Operator.system(), "t-1"),
                Outcome.success(), Duration.ofMillis(1))));
    }

    @Test
    void Environment里的键_优先于三源() {
        runner.withPropertyValues("leitu.test.bridge=from-spring").run(ctx ->
                assertThat(ctx.getBean(ConfigReader.class).get("leitu.test.bridge")).contains("from-spring"));
    }

    /**
     * 反向自测：配置中心必须压过本地 application.yml 之类的制品内基线。
     *
     * <p>若有人把整条 Environment 放回链头（回到单一 EnvironmentConfigSource），
     * 这里立刻变红——运维在配置中心改的值会被制品里写死的默认值盖掉，
     * 而症状只是"改了不生效"，极难归因。
     */
    @Test
    void 配置中心压过本地文件层() {
        runner.withPropertyValues("leitu.test.center=from-artifact")
                .withBean("center", ConfigSource.class, () -> ConfigSources.fromMap(
                        "配置中心", Map.of("leitu.test.center", "from-center")))
                .run(ctx -> assertThat(ctx.getBean(ConfigReader.class).get("leitu.test.center"))
                        .contains("from-center"));
    }

    /** 运行时覆盖（系统属性/命令行）仍在最上层——启动这一次的意志压过配置中心。 */
    @Test
    void 系统属性压过配置中心() {
        System.setProperty("leitu.test.override", "from-system-property");
        try {
            runner.withBean("center", ConfigSource.class, () -> ConfigSources.fromMap(
                    "配置中心", Map.of("leitu.test.override", "from-center")))
                    .run(ctx -> assertThat(ctx.getBean(ConfigReader.class).get("leitu.test.override"))
                            .contains("from-system-property"));
        } finally {
            System.clearProperty("leitu.test.override");
        }
    }

    @Test
    void 应用声明的配置源_插在三源之前() {
        runner.withBean("customSource", ConfigSource.class, () -> ConfigSources.fromMap(
                "测试源", Map.of("leitu.test.custom", "from-user-source"))).run(ctx ->
                assertThat(ctx.getBean(ConfigReader.class).get("leitu.test.custom"))
                        .contains("from-user-source"));
    }

    @Test
    void 缺失键_走兜底默认值() {
        runner.run(ctx ->
                assertThat(ctx.getBean(ConfigReader.class).getInt("leitu.test.missing", 42)).isEqualTo(42));
    }
}
