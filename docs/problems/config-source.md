# 配置值从哪来（config-source）

> catalog：pipeline ｜ required: always ｜ status: answered（stability: frozen，since 0.2.0） ｜ depends_on: —（根）

## 一、为什么

每一层都要读配置：判定链的阈值、缓存的 TTL、租户标识、模型名、开关。五个已立目问题以本答案为依赖（secrets-split / feature-flags / cache / lock / storage）——没有它，每个答案自造读法。

没有标准答案时的漂移成本：每个项目自造"配置读法"——手写 System.getenv / Integer.parseInt / orElse 三件套、解析失败吞掉走默认、优先级靠猜；AI 生成时每处取值都在猜"这个项目的配置从哪来"，猜错一次就是一次线上事故。

## 二、机制（标准答案的形状）

**配置源三源兜底 + SPI 插拔：一次读取沿优先级链现查，first-match-wins；优先级=组合顺序，一处可见。**

```java
// 已实现：leitu-core 的 cn.youhuale.leitu.core.config 包
public interface ConfigSource {                  // SPI：配置值的一个来源
    String name();                               // 诊断身份，永不携带值
    Optional<String> get(String key);            // 缺失=Optional.empty() 是合法态
}

public interface ConfigReader {                  // Port：沿源链现查
    Optional<String> get(String key);
    String get(String key, String fallback);
    int getInt(String key, int fallback);        // + getLong / getBoolean / getDuration / getEnum
    static ConfigReader of(ConfigSource... sources);   // 参数顺序即优先级
    static ConfigReader standard();              // 系统属性 → 环境变量 → classpath leitu.properties
}
```

- **形状**：String + 类型化助手（int/long/boolean/Duration/enum 带默认值）；缺失→默认值是正常态，有值但解析失败→错误即教程（IAE 带 key、源名、期望格式与示例——**永不带值**）
- **兜底**：`standard()` 三源——系统属性 → 环境变量 → classpath `leitu.properties`（对齐 Spring Environment 标准优先级；文件缺失=空源是常态）；环境源对点分键同时查归一形（datasource.url → DATASOURCE_URL）
- **插拔**：配置中心（Nacos/Apollo）= adapter 实现 `ConfigSource` 插入；`of(源…)` 参数顺序即优先级，toString 印出全链；读时求值无订阅——变更传播=宿主重建组件
- **成套答案**：starter 装配与配置中心 adapter 待真实痛点接入（本答案交付形状 + 兜底）

## 三、取舍

**为什么优先级=组合顺序而不是 ordinal**：优先级是装配决策不是源的属性——序号散在各源，排错要把全部源的序号拼成图；组合顺序在装配处一处可见，可 grep 可单测。

**为什么读时求值、无订阅**：系统属性/环境变量读取便宜，classpath 文件静态；动态标记+变更监听是双通道刷新承诺，core 守不住（承诺纪律）——动态性是配置中心 adapter 的事，变更传播=宿主重建组件。

**为什么解析失败抛异常而不是悄悄回退默认值**：缺失≠写错。缺失走默认是正常态；"abc" 当 int 抛教学异常——悄悄回退正是把 typo 藏进默认值的事故配方（Boolean.parseBoolean 的静默 false 即前车之鉴，故 boolean 只认 true/false）。

**为什么 Duration 只认 ISO-8601**：JDK 原生零发明、无歧义、与 Duration.toString 往返对称；"30s" 便捷后缀留给 adapter。

**为什么环境变量做键名归一**：OS 不允许环境变量含点——不归一，点分键在环境源形同虚设；Spring 同款（SystemEnvironmentPropertySource）。

**为什么值不上诊断面**：toString 与一切异常只含 key 与源名——秘密与普通配置同走此读取器的那天（secrets-split），错误路径不能变成泄漏路径；单测锁死，无豁免。

**被拒绝的备选**：Optional-only 纯 String 端口（每个消费方手写 parse 三件套的旧病）；ordinal 进源（排错拼图）；isDynamic/changeListener 进 core（守不住的刷新承诺）；@ConfigurationProperties 式绑定（类路径魔法）；快照式（断配置中心动态性）；keys() 枚举（配置倾倒与值静默为敌）；错误携带原值（教学便利换泄漏路径——教义必须无豁免）。详见 ADR-009。

## 四、边界

- 键名是世界的：点分命名（如 datasource.url）是文档惯例不是构造期强制——对照 ObservationEvent.name 强制：事件是本库协议，配置键来自外部世界
- 无 keys()/倾倒：读取按已知键；枚举招来配置倾倒，与值不上诊断面为敌
- 源抛异常大声传播，不静默降级——静默降级会把「配置中心挂了」伪装成「配置是旧值」
- 秘密（凭据）不进本答案：轮换、脱敏、绝不落日志是 secrets-split 的独立问题（变化原因不同）；本答案的值静默教义为其留缝
- 关联：ADR-009；下游见 catalog 条目 secrets-split / feature-flags / cache / lock / storage
