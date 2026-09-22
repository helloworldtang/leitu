# 操作失败了，怎么向调用方交代（failure-response）

> catalog：pipeline ｜ required: always ｜ status: answered（stability: frozen，since 0.2.0） ｜ depends_on: who-is-operating, allow-or-not, what-happened

## 一、为什么

每个失败时刻都要回答"对调用方说什么"。三个失败时刻共用同一条交代通道：业务不让做（判定链否决）/ 调用不对（参数与状态不允许）/ 我们挂了（系统错，含下游与三方故障）。

三个错位各有代价：说太少（调用方无法自救只能工单）、说太多（异常消息泄漏 SQL/路径/内部拓扑）、说太乱（每接口一套错误结构）。与既有答案的关系：Decision 对内判定、ObservationEvent 对内记录——交代是对外的第三面，不能另起一套结构。

没有标准答案时的漂移成本：每个项目自造错误结构——段位码靠查表、HTTP 状态与 body code 各说各话、系统错的异常消息直出调用方；AI 生成每处失败处理都在猜"这个项目怎么交代"，事后联调与工单排查全部失效。

## 二、机制（标准答案的形状）

**失败交代 = 一个传输无关的交代结构（FailureNotice）+ 脱敏默认：业务错全量交代，系统错只给通用交代 + traceId。**

```java
// 已实现：leitu-core 的 cn.youhuale.leitu.core.failure 包
public enum Kind { BUSINESS, SYSTEM }              // 谁的错，决定交代的尺度

public record FailureNotice(Kind kind, String type, String title, String detail,
                            Retry retry, String traceId, Map<String, String> attributes) {
    // type: 点分机读标识（构造即校验）   title: 稳定短句   detail: 本次交代
    // retry: 复用判定链 Retry（否决类失败的重试语义单一事实源）
    // traceId: 调用方凭它找支持   attributes: 扩展成员（防御拷贝）
}

public final class FailureNotices {                // 工厂（model 静态方法）
    static FailureNotice business(type, title, detail, ctx[, retry]);   // 全量交代，默认不重试
    static FailureNotice system(type, title, ctx[, retry]);             // 不收 detail——脱敏在工厂面成立
    static FailureNotice fromDecision(Decision d, ctx);   // 否决→交代：reason→detail、retry 随行
    static FailureNotice fromThrowable(Throwable t, ctx); // 系统错：异常消息与类名不出端
}
```

- **形状**：三个子问题逐一落位——分类=Kind 二分、结构=七组件、否决对齐=fromDecision 复用 Retry
- **兜底**：工厂即安全默认——business 全量；fromThrowable 脱敏（异常消息与类名不出端，全量细节走观测通道）
- **对齐**：fromDecision 保留理由与重试语义；allow 进来=装配错误大声失败
- **成套答案**：HTTP problem+json（RFC 9457）投影已在 adapter-web 落地——kind→4xx/5xx、**type URN 化**（`urn:leitu:problem:<点分机读标识>`，见下）、retry/traceId/attributes→扩展成员、status/instance 由传输层补；消息队列 / RPC / CLI 同一本体各自投影
- **为什么 type 必须 URN 化**：RFC 9457 §4.2.1 规定 type 是 URI 引用；裸点分字符串 `order.not-found` 会被当成相对路径解析到当前站点下，消费方按 URI 去拉文档时跑到错的地址。URN 说的是「这是个名字，不是地址」——不承诺解引用结果，也不随部署路径变化；为此 core 侧把 type 字符集收口到 `[A-Za-z0-9]+([._-][A-Za-z0-9]+)*`，构造期即校验

## 三、取舍

**为什么二分而不是三分（三方单列）**：分类必须带来不同的交代行为才有意义，v1 里三方错与系统错同走脱敏+traceId——分类即冗余（承诺纪律）。

**为什么 traceId 而非整个 ExecutionContext**：交代只给调用方要引用的锚点；操作者/租户与调用方无关（观测与审计才要全上下文）。

**为什么复用 Retry 不自造**：否决类失败的重试语义在判定链已定案——同一事实两处定义必漂移。

**为什么 BUSINESS 全量而 SYSTEM 强制脱敏**：调用方的错说出来无损失；我们的错里 message 可能带 SQL/路径/拓扑——脱敏默认+单测锁死，而非自觉。

**为什么 model-only（无 api/spi/internal）**：Port 的判据是"有没有可注入的运行期协作者"（对照 context 的绑定器、guard 的链执行、observe 的扇出记录器）——失败交代没有协作者，答案是"造一个值"，兜底=纯函数工厂。

**为什么事件名/type 点分命名**：机器可聚可查的键，与 ObservationEvent.name 同一命名法，构造即校验。

**被拒绝的备选**：纯数字段位错误码（人机都要查码表）；HTTP 200+body code（传输耦合）；status/instance 进 core（传输投影）；三方错单列（分类不带来不同行为）；异常消息直传 detail（泄漏内部）；core 类型名 Problem/ProblemDetail（绑定 HTTP spec 词、与 Spring 撞名）；每题必备 api Port（形式主义）。详见 ADR-011。

## 四、边界

- adapter-web 的 problem+json 投影已交付（type/title/detail/status + kind/retry/traceId/attributes）；Spring ProblemDetail 绑定未做，core 不绑定任何传输
- core 不承诺错误码注册中心与 i18n 文案（type 是标识不是码表；默认中文文案由宿主/adapter 覆盖）
- 参数验证是另一道题（input-validation：验证失败用 BUSINESS 交代，但"在哪验、验什么"不在本答案）
- 记录与交代分离：fromThrowable 不产生观测事件——记录是 what-happened 的答案，两通道相邻不合并
- 关联：ADR-011；上游 who-is-operating（traceId）/ allow-or-not（Decision、Retry）/ what-happened（全量细节的去处）
