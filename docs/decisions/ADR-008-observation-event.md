# ADR-008 问题"发生了什么"的答案：观测事件协议 + 日志级默认

状态：已接受（2026-09-13）

## 背景

"发生了什么，怎么记录"是管道无条件必答题：出事要复盘（怎么失败的）、审计要回放（谁改的什么）、成本要入账（AI 花了多少 token）。六个已立目问题以它为依赖（failure-response / data-changed-by-whom / data-access / cache / lock / storage）——没有它，每个答案自造记法。

对"一串事件 + 弱类型属性"形态的既有实践验尸，得到三条结论：

1. **Map 弱类型事件**：横切字段（谁、结果、耗时）靠约定塞字符串键，每个项目一套键名，字段漂移无法机器守护；
2. **记录产不出指标**：事件口不是指标口——聚合、采样、导出是消费侧的自由，不该挤进记录协议；
3. **默认 Noop + 全局关闭开关**：观测可被静默丢弃，等于观测不存在——与授权维度 Noop=默认放行同罪。

AI 时代新增维度：AGENT 操作者与真人同场，token 用量是不可忽略的成本事实。

## 决策

标准答案 = **观测事件协议 + 记录器**：`ObservationEvent`（强类型横切字段）经 `ObservationRecorder`（Port，一次 record 扇出）落到 `ObservationSink`（SPI，adapter 绑定日志管线 / OTel / Micrometer / 审计库）。

- **事件强类型组件**：name（**点分命名，构造即校验**——左侧是域、右侧是事实）/ context（整个 ExecutionContext：谁 + traceId，锚点复用 who-is-operating 的既有答案）/ occurredAt / outcome（sealed：Success / Failure 必带原因——错误即教程在观测侧的投影）/ duration / aiUsage / attributes（逃生门，不可变拷贝）
- **日志级默认**：空装配同样得到一台可用记录器——事件落到 JDK 日志（System.Logger 单行，失败升 WARNING）。观测维度=最小可用实现，非 Noop（分维度默认值）；sink 异常被吞并大声记错——**观测不打断主流程**
- **AI 成本**：token 用量进类型（AiUsage：model + input/output，模型侧客观事实）；**费用不进类型**——币种×批价×缓存折减的变化原因在计费方，core 的冻结承诺守不住，走 attributes（如 `"ai.cost"`）
- **三支柱定位**：logs / metrics / traces 是**同一事件流在 sink 侧的三种投影**，不是三份事实。core 只承诺事件协议（承诺纪律）；投影绑定属 adapter

包名 `observe`（高频经济学：与 context/guard 同量级）；类型名 `Observation*` 对齐业界。

## 业界对照

OpenTelemetry 语义约定：点分命名的事件/属性名、trace 关联一等公民；Micrometer Observation API（Spring Boot 3）："observation" 为业界词、单事件多投影消费；三支柱投影模型为可观测性公共常识；System.Logger 是 JDK 9+ 唯一零框架依赖的日志门面（宿主可路由到实际日志实现）；OpenAI / Anthropic 的 usage 报数均以 input/output token 为准。

## 后果

- 事件名、锚点、结果语义在构造期强制——违反被单测抓住（每个约定能指着测试说"违反会被谁抓住"）
- 下游答案（cache/lock/storage 的命中率与延迟、failure-response 的失败原因、审计回放的 who+traceId）在同一事件流上投影，不再各造记法
- adapter 层获得明确接缝：实现 ObservationSink 即接入任意观测后端

**被拒绝的备选**：

- **Noop 默认 + 全局关闭开关**——观测可静默消失即观测不存在；分维度默认值明令废除
- **三支柱进 core（三套 API）**——承诺聚合、采样、span 树，机器守不住（承诺纪律）；且三支柱是消费侧投影，不是三份事实
- **Map 弱类型事件**——横切字段无处安放、键名逐项目漂移（验尸结论 1）
- **费用入 AiUsage 类型**——计费口径随 provider 月度变价，core 冻结承诺守不住
- **Observer / Telemetry 命名**——前者撞 GoF 观察者模式（唯一指称不过关），后者承诺超出"记录"这一个能被机器守护的动作
