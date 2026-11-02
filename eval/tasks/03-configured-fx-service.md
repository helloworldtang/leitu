# 任务 03 · 配置驱动的汇率换算服务

任务卡版本：**v1**（2026-10-27 首次发布）。目标答案面：**config-source + what-happened**（前两条任务未覆盖的两个面），叠加任务 01 的管道行为。

**v2**（2026-10-28）：只改阅读面，判据一字未动——金样本从「整目录」改为点名到文件（`pom.xml` + `src/main/**`），
并把上下文预算写进卡上（由 `EvalContextBudgetTest` 守）。首采时这张卡的按序读实测
**6.2 万字符**，其中金样本整目录占 60%（含三个金样本自己的测试——它们不是「照写姿势」的必需品）。
v2 已于当日重采（**8/8、一次绿灯是、180 秒、13 条歧义**，见 [baselines](../baselines.md)）。
预算当日二次校准 4.8 → **5.0 万**：同批修复往接入清单与两个问题页补了两条**必需事实**（`ConfigReader` 的四层源链、
失败投影的 kind→status 映射表），必读面一字未减、文档合计 +3.5k 字符——按守卫的口径属「多读的部分值得」，
硬上限 5 万未动（这张卡正好压线，余量 29 字符，事实上已无法再吸收任何新事实）。

**v3**（2026-10-30）：**只改阅读面，判据一字未动**——六个问题页里的
`who-is-operating` / `allow-or-not` / `api-contract` 三页从**必读降为按需**，预算 5.0 → **4.5 万**。
依据不是「预算超了所以砍」，是执行者原话（v2 留痕）：

> 六个问题页里 `who-is-operating` / `allow-or-not` / `api-contract` 的两段对本次实现信息量很低
> （Bean 名与 403 语义在 `INTEGRATION` 里已有），而真正缺的两件——**`ConfigReader` Bean 的源链**、
> **失败投影的状态码映射表**——**恰好都不在这六页里**。
> 这条比「预算超没超」重要：**预算压得再低，压不到「该写却没写」的那一页**。

**所以这一刀分两半，顺序不能反**：先确认「该写却没写」的两件已补进必读面（`INTEGRATION` §2 源链表、
`failure-response` 的 kind→status 表），再降这三页——否则收窄就是在挖新的信息缺口。
降为按需后，验收 6/7 的依据仍在必读面里（见下面「按需」段的落点表），不依赖被降的三页。
实测 49,971 → **43,641**（−6,330），预算 4.5 万，余量 1,359。

**v3 未重新采样**：v2 的 8/8、180 秒、13 条歧义只属于 v2 的阅读面。

## 目标

在空目录创建一个可运行的 Spring Boot 汇率换算服务：`GET /fx/convert?from=USD&to=CNY&amount=100`，汇率**从配置来**，每次调用**记录观测事件**，并满足下面验收的行为。

## 按序读（只读，仓库内；顺序即执行顺序）

上下文预算：**≤ 4.5 万 Unicode 字符**（`wc -m` 口径，**不是字节**——中文一字约 3 字节；
编号项全文合计、上界口径；机器守卫 `EvalContextBudgetTest`，超了构建即红）。

1. **`docs/INTEGRATION.md` 全文**——接入清单，按执行顺序排；本任务的验收 6/7 依据也在其中（见按需段落点表）；
2. 与本任务验收直接相关的三个问题页，**只读「二、机制」与「四、边界」两段**（按段读；预算按全文上界计）：
   `docs/problems/config-source.md`、`docs/problems/what-happened.md`、`docs/problems/failure-response.md`；
3. 金样本 **`examples/spring-boot/pom.xml`** + **`examples/spring-boot/src/main/**`**、
   **`examples/what-happened/pom.xml`** + **`examples/what-happened/src/main/**`**、
   **`examples/config-source/pom.xml`** + **`examples/config-source/src/main/**`**——照写姿势，不要发明。

不得阅读：`eval/reference/**`（参考解）、`eval/runs/**`、`eval/baselines.md`。

**按需**（故意不编号、不计入预算——编号项才是必读面）：
`docs/problems/who-is-operating.md`、`docs/problems/allow-or-not.md`、`docs/problems/api-contract.md`、
`catalog/problems.json`、`docs/GLOSSARY.md`、金样本的 `src/test/**`。
这三页在 v2 里是必读，v3 降为按需——**降级不是因为没用，是因为它们承担的内容已在必读面里**：

| 验收 | 依据落在必读面的哪一段 | 原来靠哪一页 |
|---|---|---|
| 4a（注册 Sink + 耗时非空） | `what-happened` §二——**它本来就是必读页**，`ObservationSink.accept` 的完整签名也在那里 | —（无需替代） |
| 4b（操作者与 traceId 来自执行上下文） | `INTEGRATION` §4 绑定姿势，§231 讲成功响应怎么带 `X-Trace-Id` | `who-is-operating` |
| 6（非授权主体 → 403 `guard.denied`） | `INTEGRATION` §7 失败表（§227）+ §280 自检段给的具体期望 | `allow-or-not` |
| 7（`operationId` 在文档中） | `INTEGRATION` §7（§257：operationId 与动作名同源是**约定不是机制**，写错不报错） | `api-contract` |

> 这张表 v3 初版把验收 4 整条归到 `who-is-operating` 名下，是**错的**：验收 4 含两个信息需求，
> 「注册 Sink + 耗时非空」在 `what-happened`（本就必读），只有「操作者 / traceId 从上下文取」那半在 §4。
> 2026-11-02 采样执行者指出后改为按**信息需求**归、不按验收条目的编号归——一条验收可以有多个信息需求，
> 按条目归会把两个来源压成一行，看着齐整、实际指错门。

诚实边界：上表的落点是**验收方回查后标注的**，不是执行者验证过的——
执行者若在某条上找不到依据，按卡上的口径记一条歧义点，那就是这张卡收窄收过头了。

## 验收（全过才算完成）

1. `mvn test` 全绿，且**第 3～7 条行为各有至少一个测试断言**——零测试时 surefire 也会绿，那种绿不计入「一次绿灯」；
2. 引依赖即用：下列 Bean 在上下文里就位（按类型取即可，不必知实现）——
   `ExecutionContextBinder` / `ExecutionContextReader` / `GuardChain` / `ConfigReader` /
   `ObservationRecorder` / `JdbcDataStoreFactory` / `OpenAPI`（末者需自引 springdoc；本任务无库表，`JdbcDataStoreFactory` 就位即可，不强制用库）；
3. **换算由配置驱动**：汇率值来自配置源（如 `fx.rate.USD-CNY`），改配置行为跟着变——测试里换一组配置值断言结果变化；
4. **每次调用记录观测事件**：注册一个自定义事件落点（Sink），捕获并断言——事件名点分（如 `fx.converted`）、操作者与 traceId 来自执行上下文、耗时非空；
5. 失败投影：未知币对 → 业务错 problem+json（服务内统一一种状态码，`fx.pair-not-found` 类标识）；
6. 守卫：非授权主体调用 → 403 `urn:leitu:problem:guard.denied`；
7. `/v3/api-docs` 中 operationId（`fx.convert`）在文档中；
8. 登记：完成率 / 一次绿灯 / 时长 / 歧义点清单。

## 读数口径（与任务 01/02 一致）

- 完成率：验收逐条自评，能自证给命令，不能自证明说未验证；
- 一次绿灯：首次 `mvn test` 是否全绿；
- 时长：`date +%s` 自任务开始至首次测试结束；
- 歧义点：每次「文档没说清、我不得不猜」记一条（`位置 | 我猜了什么 | 依据`）。
