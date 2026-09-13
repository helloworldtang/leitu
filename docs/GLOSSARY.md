# 术语表（GLOSSARY）

本仓库的词，只在这里定义后使用。新术语入库过三关：**唯一指称 / 业界对齐 / 不携带无法兑现的承诺**。

## 项目身份

| 术语 | 定义 |
|---|---|
| **累土（leitu）** | 本项目名。出自《道德经》"九层之台，起于累土"——复杂系统起于累积的标准答案。坐标 `cn.youhuale:leitu-*`，包 `cn.youhuale.leitu.*`。 |
| **标准答案库** | 累土的自我定位：把反复出现、边界清楚的问题的答案固化成库。人得到确定性，AI 得到无歧义的生成目标和自验信号。 |
| **答案五件套** | 一条答案入库的完整单位：问题页 + 代码 + 守护规则 + 金样本 + 目录条目。五件缺一不可（CI 强制）。 |
| **问题目录（catalog）** | `catalog/problems.json`——本库回答哪些问题的总索引，机器可读，覆盖度可测。 |

## 结构

| 术语 | 定义 |
|---|---|
| **core** | 执行管道的**端口面**（必答问题的唯一接口）+ 跨模块协作的**调度机制**（OperationDispatcher）。纯 JDK、零框架依赖、变化≈冻结。模块加载与生命周期委托宿主。 |
| **capability** | 领域能力的完整答案包（api + spi + model + 默认实现 + 装饰器）。业务按需取用，框架没有它照样跑（选做题）。 |
| **adapter** | 唯一接触外部中间件/技术的一层（驱动）。 |
| **starter** | 装配层：引依赖即拿到可用答案（发行版组装）。 |
| **宿主（host）** | 承载生命周期的运行环境（如 Spring Boot）。累土是"宿主之上的微内核"。 |
| **分层组织，微内核组装** | 架构风格定案：编译期代码按 starter→adapter→capability→core 单向分层组织；运行期应用以 core 为内核组装，模块协作必经 Dispatcher。见 ADR-003。 |

## 接口

| 术语 | 定义 |
|---|---|
| **Port** | api 面接口：应用代码/能力代码调用的稳定编程面。一个 Port 对应一个必答问题。 |
| **SPI** | spi 面接口：适配器/上层实现的扩展点。 |
| **必答题 / 选做题** | core 回答管道必答题（每个操作都在场）；capability 回答选做题（按需启用）。 |
| **三段交付** | 每道必答题的交付物：形状（core 窄接口）+ 兜底（core 内置默认值，分维度安全默认）+ 成套答案（capability + starter 默认接线）。答案的完整单位是目录条目，不是 artifact。 |

## 判定链

| 术语 | 定义 |
|---|---|
| **判定链（Guard Chain）** | "允许吗"的标准答案：一串可插拔 Guard，任一可否决，输出统一 Decision。入口判定链（进来的请求）与出站判定链（调出去的请求）双执行点、同一协议。见 ADR-005。 |
| **Guard** | 判定链上的一类否决源：功能权限 / 数据权限 / 限流 / 配额 / 熔断 / 维护窗口 / 风控……各自独立演化，共享同一协议。 |
| **Decision** | 判定结果对象：`verdict（allow/deny）+ reason（为什么）+ retry（重试语义）`。否决必须带"为什么 + 怎么办"（错误即教程）。 |
| **OperationDispatcher** | core 内的跨模块协作调度机制：in-process、上下文传播 / 鉴权 / 观测在环。模块间不许直插对方 internal。 |

## 观测

| 术语 | 定义 |
|---|---|
| **观测（observation）** | "发生了什么、怎么记录"的答案域词。包名 `observe`（高频经济学），类型名 `Observation*`（对齐 Micrometer Observation API 与 OTel 语义约定）。见 ADR-008。 |
| **观测事件（ObservationEvent）** | 统一事件协议：name（点分命名，构造即校验）+ context（谁 + traceId，锚点复用 who-is-operating）+ occurredAt + outcome（成功/失败必带原因）+ duration + aiUsage + attributes（逃生门，不可变拷贝）。 |
| **观测记录器（ObservationRecorder）** | Port：`record(event)` 一次记录、扇出到全部落点；空装配落到日志级默认（观测维度=最小可用实现，非 Noop）。 |
| **观测落点（ObservationSink）** | SPI：事件的落点，adapter/宿主实现（日志管线 / OTel / Micrometer / 审计库）。三支柱是同一事件流在落点侧的投影；约定不抛异常——观测不打断主流程。 |
| **AiUsage** | AI 调用的用量事实：model + input/output token 数。费用不走类型（计费口径变化原因在 provider 侧），走 attributes（如 `"ai.cost"`）。 |

## 配置

| 术语 | 定义 |
|---|---|
| **配置源（ConfigSource）** | SPI——配置值的一个来源。`name()`（诊断身份，永不携带值）+ `get(key)`（缺失=Optional.empty 是合法态）。配置中心（Nacos/Apollo）由 adapter 实现本接口插入。源不声明优先级——优先级=组合顺序。见 ADR-009。 |
| **配置读取器（ConfigReader）** | Port——沿源链现查（first-match-wins，读时求值无订阅）。`get(key)` / `get(key, fallback)` + 类型化助手（int/long/boolean/Duration/enum 带默认值）；缺失→默认值，有值但解析失败→错误即教程。`standard()`=三源兜底（系统属性→环境变量→classpath leitu.properties）。 |
| **优先级=组合顺序** | 配置源不各自声明优先级（无序号/注解）；`ConfigReader.of(源…)` 参数顺序即优先级，装配处一处可见（toString 印出全链）。 |
| **值不上诊断面** | 配置的安全教义：toString 与一切异常只含 key 与源名，永不携带配置值——无豁免，为秘密源（secrets-split，待答）留缝，被单测锁死。 |

## 制度

| 术语 | 定义 |
|---|---|
| **承诺纪律** | 框架只承诺能被机器守护的事：词要能兑现、目标要能被测试/CI 守住、差异要经得起对标。见 ADR-004。 |
| **分维度默认值** | 兜底实现的安全教义：无害维度（缓存/事件）可 Noop；安全维度（授权）deny-by-default；观测维度给最小可用实现（如日志）。 |
| **金样本（golden example）** | examples/ 里的可运行最小示范：既是 AI 的学习样本，又是框架改动的金丝雀。 |
| **术语三关** | 唯一指称 / 业界对齐 / 不携带无法兑现的承诺。 |

## 限用词表

| 词 | 限定 |
|---|---|
| **契约** | 仅用于 Design by Contract 语境（前置/后置条件、不变量）。不作为接口的统称（与 DDD 契约、Pact 消费者驱动契约三重歧义）。 |
| **kernel** | 不用于模块名/层名（核心模块叫 core）。仅可在讲架构谱系的行文中出现。 |
| **runtime** | 英文词 runtime 不用于描述累土：不作身份（累土不是 runtime，生命周期委托宿主）、不作模块名、不作阶段定语——阶段一律用「运行期」（与「编译期」对偶）。仅两类场合出现：本限用词表自身；指外部事物（宿主运行时、JVM runtime 等第三方语境）。 |
| **IAM** | 系统维度的名称（独立身份平台），不用于累土的能力模块名。对应能力叫 identity / access。 |
