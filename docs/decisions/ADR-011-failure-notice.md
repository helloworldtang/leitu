# ADR-011 问题"操作失败了"的答案：FailureNotice 交代结构 + 脱敏默认

状态：已接受（2026-09-13）

## 背景

每个失败时刻都要回答"对调用方说什么"。三个错位各有代价——说太少（调用方无法自救只能工单）、说太多（异常消息泄漏 SQL/路径/内部拓扑）、说太乱（每接口一套错误结构）。与既有答案的衔接：Decision（对内判定）与 Outcome.Failure（对内记录）已有，交代是对外的第三面，不能另起一套结构。

对常见错误处理实践验尸得三条（独立论证）：①**纯数字段位错误码**——人与机器都要查码表才能解码，扩展段位=改中心注册表；②**HTTP 200 + body code 混合模式**——传输耦合（消息队列/RPC/CLI 没有"200"），且重造了传输层已标准化的分类；③**异常消息直传调用方**——系统错的 message 可能带 SQL 片段、路径、内部拓扑，泄漏面不可控。

## 决策

标准答案 = **FailureNotice：传输无关交代本体 + 脱敏默认工厂**。

- **七组件**：kind（二分：BUSINESS=调用方的错，全量交代；SYSTEM=我们的错含下游三方故障，脱敏交代——从调用方视角我们的依赖挂了就是我们的错）/ type（点分机读标识，构造即校验）/ title（稳定短句）/ detail（本次交代）/ retry（**复用判定链 Retry**——否决类失败的重试语义在判定链已定案，同一事实两处定义必漂移）/ traceId（调用方凭它找支持；不给整个 ExecutionContext——操作者/租户与调用方无关）/ attributes（扩展成员，防御拷贝）
- **工厂即兜底**：business(...) 全量交代（默认不重试——改行为，别重试同样的错）；system(...) **不收 detail 参数**（脱敏教义在工厂面成立：经 system/fromThrowable 造出的交代不可能泄漏）；fromDecision(...)（ALLOW 进来=装配错误大声失败；DENY→reason→detail、retry 随行）；fromThrowable(...)（SYSTEM 常量交代，**完全不用异常内容**——异常消息与类名不出端；全量细节走观测通道）
- **Decision 为本体，HTTP 为投影**：RFC 9457 problem details 是 adapter 侧投影（kind→4xx/5xx、type 可 URN 化、retry/traceId/attributes→扩展成员、status/instance 由传输层补），core 不绑定 HTTP
- **model-only 包**（无 api/spi/internal）：Port 的判据是"有没有可注入的运行期协作者"（对照 context 的绑定器、guard 的链执行、observe 的扇出记录器），不是每题必备一个——失败交代没有协作者，答案是"造一个值"，兜底=纯函数工厂；为均匀造转发类是形式主义

## 业界对照

RFC 9457（RFC 7807 修订）problem details——对外错误结构的业界正典，本答案的结构投影目标；HTTP 4xx/5xx 语义分类即 kind 的传输投影；Spring 6 ProblemDetail / zalando problem 为 JVM 生态同款绑定（属 adapter，待建）；点分 type 对齐本库 ObservationEvent.name 与 OTel 语义约定的命名法。

## 后果

- 调用方拿到的一律同构；脱敏默认被单测锁死（fromThrowable 不泄漏被哨兵验证）；否决/业务/系统失败共用一条交代通道；adapter 只投影不决策
- 代价：二分较粗（三方错归 SYSTEM，等真实痛点）；type 命名空间无注册中心（点分域前缀自律，可加 CI）；默认中文文案由宿主/adapter 按需覆盖

**被拒绝的备选**：①纯数字段位错误码（人机都要查码表、扩展改中心注册表）②HTTP 200+body code（传输耦合，与本体/投影定案冲突）③status/instance 进 core（传输投影，RFC 9457 自身也定为传输层职责）④第三方错单列 THIRD_PARTY（v1 交代行为与 SYSTEM 无差异——分类不带来不同行为=不分类，承诺纪律）⑤Throwable.message 直传 detail（泄漏内部）⑥core 类型名 Problem/ProblemDetail（本体名绑定 HTTP spec 词、与 Spring ProblemDetail 撞名，唯一指称不过关）⑦每题必备 api Port（无协作者可装配的纯转发是形式主义）。
