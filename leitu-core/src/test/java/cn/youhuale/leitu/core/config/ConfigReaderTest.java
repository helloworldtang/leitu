package cn.youhuale.leitu.core.config;

import cn.youhuale.leitu.core.config.api.ConfigReader;
import cn.youhuale.leitu.core.config.api.ConfigSources;
import cn.youhuale.leitu.core.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 配置答案的单测：锁优先级语义、类型化教学异常与值静默教义。 */
class ConfigReaderTest {

    private static final String SENTINEL = "super-secret-token";

    @Test
    void 三源兜底_系统属性最高_环境与classpath依次兜底() {
        ConfigReader config = ConfigReader.standard();
        // classpath 值直接命中（测试类路径有 leitu.properties）
        assertEquals("file-value", config.get("leitu.config.test.greeting", "默认"));
        // 系统属性盖过 classpath
        System.setProperty("leitu.config.test.greeting", "from-sysprop");
        try {
            assertEquals("from-sysprop", config.get("leitu.config.test.greeting", "默认"));
        } finally {
            System.clearProperty("leitu.config.test.greeting");
        }
        // 读时求值：系统属性移除后回到文件值
        assertEquals("file-value", config.get("leitu.config.test.greeting", "默认"));
    }

    @Test
    void 优先级等于组合顺序_前源赢_顺序印在toString() {
        ConfigSource high = ConfigSources.fromMap("高源", Map.of("k", "high"));
        ConfigSource low = ConfigSources.fromMap("低源", Map.of("k", "low"));
        ConfigReader config = ConfigReader.of(high, low);
        assertEquals("high", config.get("k", "默认"));
        String text = config.toString();
        assertTrue(text.contains("高源") && text.contains("低源") && text.contains("→"),
                "toString 必须印出全链优先级：" + text);
    }

    @Test
    void 缺失返回Optional空_字符串与typed全走fallback() {
        ConfigReader config = ConfigReader.of();
        assertEquals(Optional.empty(), config.get("no.such.key"));
        assertEquals("默认", config.get("no.such.key", "默认"));
        assertEquals(7, config.getInt("no.such.key", 7));
        assertEquals(false, config.getBoolean("no.such.key", false));
        assertEquals(Duration.ofSeconds(5), config.getDuration("no.such.key", Duration.ofSeconds(5)));
    }

    @Test
    void 有值但解析失败_教学异常含key源名格式示例() {
        ConfigReader config = ConfigReader.standard();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.getInt("leitu.config.test.bad-int", 3));
        String msg = e.getMessage();
        assertTrue(msg.contains("leitu.config.test.bad-int"), "含 key：" + msg);
        assertTrue(msg.contains("classpath:leitu.properties"), "含源名：" + msg);
        assertTrue(msg.contains("十进制整数"), "含期望格式：" + msg);
        assertTrue(msg.contains("42"), "含示例：" + msg);
    }

    @Test
    void 教学异常与toString永不携带原值_值静默教义无豁免() {
        // 每条 typed 错误路径：值内嵌哨兵，断言异常消息零携带
        String v = "x-" + SENTINEL;
        ConfigSource tricky = ConfigSources.fromMap("哨兵源", Map.of(
                "bad.int", v,
                "bad.long", v,
                "bad.boolean", v,
                "bad.duration", v,
                "bad.mode", v));
        ConfigReader config = ConfigReader.of(tricky);
        java.util.function.Consumer<Runnable> assertSilent = run -> {
            try {
                run.run();
                fail("应抛教学异常");
            } catch (IllegalArgumentException e) {
                assertFalse(e.getMessage().contains(SENTINEL),
                        "教学异常永不携带值（无豁免）：" + e.getMessage());
            }
        };
        assertSilent.accept(() -> config.getInt("bad.int", 0));
        assertSilent.accept(() -> config.getLong("bad.long", 0L));
        assertSilent.accept(() -> config.getBoolean("bad.boolean", false));
        assertSilent.accept(() -> config.getDuration("bad.duration", Duration.ZERO));
        assertSilent.accept(() -> config.getEnum("bad.mode", Mode.class, Mode.DEV));
        assertFalse(config.toString().contains(SENTINEL), "toString 永不携带值");
        assertEquals("哨兵源", tricky.toString());
    }

    @Test
    void boolean只认真假_yes与1是教学异常不静默false() {
        ConfigReader config = ConfigReader.standard();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.getBoolean("leitu.config.test.bad-boolean", false));
        assertTrue(e.getMessage().contains("true 或 false"), "必须教格式：" + e.getMessage());
        assertTrue(config.getBoolean("leitu.config.test.tls", false));
    }

    @Test
    void Duration仅ISO8601_30s是教学异常含PT示例() {
        ConfigReader config = ConfigReader.standard();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.getDuration("leitu.config.test.bad-duration", Duration.ZERO));
        assertTrue(e.getMessage().contains("PT30S"), "必须给 ISO-8601 示例：" + e.getMessage());
        assertEquals(Duration.ofSeconds(30), config.getDuration("leitu.config.test.timeout", Duration.ZERO));
    }

    enum Mode { PROD, DEV }

    @Test
    void enum大小写敏感_非法值异常列出全部合法名() {
        ConfigReader config = ConfigReader.standard();
        assertEquals(Mode.PROD, config.getEnum("leitu.config.test.mode", Mode.class, Mode.DEV));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> config.getEnum("leitu.config.test.bad-mode", Mode.class, Mode.DEV));
        assertTrue(e.getMessage().contains("PROD") && e.getMessage().contains("DEV"),
                "必须列出全部合法名：" + e.getMessage());
    }

    @Test
    void key为null或空白_教学异常() {
        ConfigReader config = ConfigReader.of();
        NullPointerException npe = assertThrows(NullPointerException.class, () -> config.get(null));
        assertTrue(npe.getMessage().contains("key 必填"));
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> config.get("  "));
        assertTrue(iae.getMessage().contains("检索键"));
    }

    @Test
    void fallback传null_教学异常指路get() {
        ConfigReader config = ConfigReader.of();
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> config.get("any.key", null));
        assertTrue(e.getMessage().contains("get(key)"), "必须指路 Optional 版：" + e.getMessage());
    }

    @Test
    void of空数组合法_全走默认值() {
        ConfigReader config = ConfigReader.of();
        assertEquals("ConfigReader{空装配，全部读取走默认值}", config.toString());
        assertEquals(42, config.getInt("anything", 42));
    }

    @Test
    void of混入null源_教学NPE() {
        NullPointerException e = assertThrows(NullPointerException.class,
                () -> ConfigReader.of(ConfigSources.fromMap("a", Map.of()), null));
        assertTrue(e.getMessage().contains("of()"), "必须给出修复方式：" + e.getMessage());
    }

    @Test
    void 环境变量宽松键名归一_精确优先于归一() {
        ConfigSource env = ConfigSources.environmentVariables(k -> {
            if (k.equals("LEITU_CONFIG_TEST_GREETING")) {
                return "from-relaxed-env";
            }
            if (k.equals("leitu.config.test.greeting")) {
                return "from-exact-env";
            }
            return null;
        });
        ConfigReader config = ConfigReader.of(env);
        assertEquals("from-exact-env", config.get("leitu.config.test.greeting", "默认"));

        ConfigSource relaxedOnly = ConfigSources.environmentVariables(k ->
                "LEITU_CONFIG_TEST_GREETING".equals(k) ? "from-relaxed-env" : null);
        assertEquals("from-relaxed-env",
                ConfigReader.of(relaxedOnly).get("leitu.config.test.greeting", "默认"));
    }

    @Test
    void relaxed归一形直测_点划线转下划线大写() {
        assertEquals("DATASOURCE_URL", cn.youhuale.leitu.core.config.internal.EnvironmentSource.relaxed("datasource.url"));
        assertEquals("MY_FLAG", cn.youhuale.leitu.core.config.internal.EnvironmentSource.relaxed("my-flag"));
    }

    @Test
    void classpath文件缺失是空源不抛_name照报() {
        ConfigSource missing = ConfigSources.classpathProperties("no-such-file.properties");
        assertEquals(Optional.empty(), missing.get("any"));
        assertEquals("classpath:no-such-file.properties", missing.name());
    }

    @Test
    void 源抛异常大声传播_不静默降级() {
        ConfigSource broken = new ConfigSource() {
            @Override
            public String name() {
                return "坏源";
            }

            @Override
            public Optional<String> get(String key) {
                throw new IllegalStateException("配置中心连接失败");
            }
        };
        ConfigReader config = ConfigReader.of(broken);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config.get("k"));
        assertTrue(e.getMessage().contains("配置中心连接失败"));
    }

    @Test
    void standard全链顺序可见() {
        String text = ConfigReader.standard().toString();
        assertTrue(text.contains("系统属性") && text.contains("环境变量")
                && text.contains("classpath:leitu.properties"), "toString 印全链：" + text);
    }

    @Test
    void typed全类型命中源值() {
        ConfigReader config = ConfigReader.standard();
        assertEquals(8, config.getInt("leitu.config.test.pool", 0));
        assertEquals(8L, config.getLong("leitu.config.test.pool", 0L));
        assertTrue(config.getBoolean("leitu.config.test.tls", false));
        assertEquals(Duration.ofSeconds(30), config.getDuration("leitu.config.test.timeout", Duration.ZERO));
        assertEquals("file-value", config.get("leitu.config.test.greeting", "默认"));
    }
}
