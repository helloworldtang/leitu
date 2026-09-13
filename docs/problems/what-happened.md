# 发生了什么（what-happened）

> catalog：pipeline ｜ required: always ｜ status: answered（stability: frozen，since 0.1.0） ｜ depends_on: who-is-operating

## 一、为什么

每个操作都要留痕，三种刚需都吃同一条事件流：出事要复盘（怎么失败的、失败在哪个环节）、审计要回放（谁在何时改了什么）、成本要入账（AI 花了多少 token）。

六个已立目问题以本答案为依赖（failure-response / data-changed-by-whom / data-access / cache / lock / storage）——没有它，每个答案自造记法。AI 时代再加一条：AGENT 操作者与真人同场，token 用量是不可忽略的成本事实（观测要能按操作者类型分账）。

没有标准答案时的漂移成本：每个项目自造"日志打法"——字段随口起名、上下文时有时无、token 用量记在谁也不认识的键下；AI 生成时漂移更甚（每处横切都要猜"这个项目怎么记"），事后检索与聚合全部失效。

## 二、机制（标准答案的形状）

**观测事件 + 记录器：一次 record、扇出落点；锚点不缺席、兜底不静默。**

```java
// 已实现：leitu-core 的 cn.youhuale.leitu.core.observe 包
public interface ObservationRecorder {          // Port：业务认识的全部接口
    void record(ObservationEvent event);
}

public record ObservationEvent(
        String name,             // 点分命名（如 "order.cancelled"），构造即校验
        ExecutionContext context,// 锚点：谁 + traceId（复用 who-is-operating，永不缺席）
        Instant occurredAt,
        Outcome outcome,         // 成功 / 失败（失败必带原因，错误即教程）；纯事实事件可不带
        Duration duration,       // 可选：耗时（延迟观测的最小输入）
        AiUsage aiUsage,         // 可选：AI 用量（model + input/output token；费用走 attributes）
        Map<String, String> attributes)  // 逃生门：领域自有细节，不可变拷贝
{ }
```

- **形状**：横切字段全部强类型——谁、结果、耗时、token 用量是检索与聚合的键，不靠字符串键约定
- **兜底**：空装配同样可记——事件落到 JDK 日志单行输出（失败升 WARNING）。观测维度=最小可用实现，非 Noop（分维度默认值，见 GLOSSARY）；落点抛异常被吞并大声记错，**观测不打断主流程**
- **三支柱**：logs / metrics / traces 是同一事件流在落点（`ObservationSink` SPI）侧的三种投影——adapter 实现 sink 接日志管线 / OTel / Micrometer（待建），core 只承诺事件协议
- **成套答案**：本答案交付形状 + 兜底；三支柱绑定（adapter）与进程内聚合/费用报表（capability）待真实痛点升级后接入

## 三、取舍

**为什么一个事件协议，而不是三套支柱 API**：三支柱消费同一份事实，是投影不是事实本身。core 只承诺能被机器守护的事件协议；聚合、采样、导出是落点侧的自由（承诺纪律，见 ADR-004）。业界佐证：Micrometer Observation API 即"单事件、多投影消费"。

**为什么 context 整个进事件（不只塞 traceId）**：谁与 traceId 同源、同时确定、同行传播——审计回放与判定复盘直接可用，不必再查一遍（决策坍缩：复用 who-is-operating 的既有答案）。

**为什么 token 进 core、费用不进**：token 是模型侧客观事实（OpenAI / Anthropic usage 报数同款约定），能被测试守住；费用=币种×批价×缓存折减，变化原因在计费方——core 的冻结承诺守不住，走 attributes（`"ai.cost"`）。

**为什么默认是日志而非 Noop**：观测被静默丢弃=观测不存在。日志级是"最小可用"——能 grep、能回放、零依赖（System.Logger，宿主可路由到实际日志实现）。

**为什么事件名构造期强制点分命名**：点分名是事件可聚可查的键（左侧域、右侧事实）。约定必须能被测试抓住（见 AGENTS.md）——现在强制是三行代码，将来改是全量数据迁移。

**被拒绝的备选**：Noop 默认 + 全局关闭开关（观测可静默消失，分维度默认值明令废除）；三支柱进 core（承诺聚合与采样，守不住）；Map 弱类型事件（横切字段无处安放、键名逐项目漂移）；费用入类型（计费口径月度变价）。详见 ADR-008。

## 四、边界

- 采样、聚合、导出到 OTel / Micrometer 属 adapter（落点投影）；core 不承诺 metrics / traces 的存在
- record 无返回值：观测是记录通道，不是查询通道
- 高频事件（如每条 SQL）记不记由调用方决策，core 不做采样策略
- 关联：ADR-008；下游见 catalog 条目 failure-response / data-changed-by-whom / data-access / cache / lock / storage
