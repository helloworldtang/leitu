# ADR-009 问题"配置值从哪来"的答案：三源兜底 + SPI 插拔配置源

状态：已接受（2026-09-13）

## 背景

配置是管道无条件必答题：五个已立目问题以它为依赖（secrets-split / feature-flags / cache / lock / storage）——没有它，每个答案自造读法。对既有实践验尸得三条结论：

1. **端口 Optional-only 无类型化**——每个消费方手写 Integer.parseInt / Duration.parse + orElse，解析错误各自吞各自吐，无一致教学；
2. **优先级=序号散落各源**——排错要把全部源的序号拼成图才知道谁赢；
3. **动态标记 + 变更监听双通道刷新**——core 层守不住的承诺（监听的时序、失败、传播范围都无测试可锁）。

## 决策

标准答案 = **配置源 SPI + 读取器 Port**：

- **三源兜底** `standard()`：系统属性 → 环境变量 → classpath `leitu.properties`（对齐 Spring Environment 标准优先级；文件缺失=空源是常态）
- **优先级=组合顺序**：`ConfigReader.of(源…)` 参数顺序即优先级（first-match-wins），toString 印出全链——装配决策在装配处，一处可见
- **类型化助手进端口**：getInt / getLong / getBoolean / getDuration / getEnum 带默认值——直接修验尸结论 1
- **缺失≠写错**：缺失→默认值是正常态；有值但解析失败→错误即教程（key + 源名 + 期望格式 + 示例），绝不静默回退（Boolean.parseBoolean 的静默 false 是前车之鉴）
- **值不上诊断面（无豁免）**：toString 与一切异常只含 key 与源名，永不携带配置值——解析失败的教学异常也不带值。为 secrets-split 留缝：秘密源实现的是同一个 SPI、被同一个读取器读，错误路径不能变成泄漏路径
- **读时求值、无订阅**：每次 get 现查链（系统属性/环境变量便宜，classpath 文件构造时装载一次）；动态性是配置中心 adapter 的事，变更传播=宿主重建组件
- **Duration 仅 ISO-8601**（JDK Duration.parse）；**环境源键名归一**（datasource.url → DATASOURCE_URL，仅环境源内做）；**源异常大声传播**（静默降级会把"配置中心挂了"伪装成"配置是旧值"）

## 业界对照

Spring Environment / PropertySource 链的 first-match-wins 与标准优先级（系统属性 > 环境变量 > application.properties）；宽松绑定对齐 Spring SystemEnvironmentPropertySource；十二要素应用第 III 条（配置存环境变量）；JDK Duration.parse 与 Duration.toString 往返对称。Spring 的绑定错误消息携带原值是被拒点——它没有值静默教义，本库有。

## 后果

- 类型化缺口修复：消费方不再手写 parse 三件套；解析错误一律同构教学
- adapter 获得明确接缝：实现 ConfigSource 即接 Nacos/Apollo，声明式排序=装配处排
- secrets-split 到来时零协议变更插同一读取器（值静默已为其锁死泄漏面）
- 每条约定可指认测试：优先级链 toString 可断言、教学异常属性可断言、值静默有哨兵单测

**被拒绝的备选**：

- **Optional-only 纯 String 端口**——验尸结论 1 的旧病：每个项目手写 parse、解析错误形态各异
- **ordinal/优先级进源**——优先级是装配决策不是源的属性，散落即排错拼图
- **isDynamic + changeListener 进 core**——双通道刷新承诺守不住（承诺纪律）
- **@ConfigurationProperties 式绑定**——类路径魔法，core 零框架依赖不容
- **快照式配置对象**——断配置中心动态性
- **getDouble / keys() 枚举**——冻结面 YAGNI；枚举招来配置倾倒，与值静默为敌
- **错误携带原值**——教学便利换泄漏路径：秘密源解析失败时异常进日志=秘密进日志；教义必须无豁免
- **"30s" 便捷时长**——发明（m 是分还是月的歧义前科）；adapter 可后补
