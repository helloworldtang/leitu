# 任务 03 · 配置驱动的汇率换算服务

任务卡版本：**v1**（2026-10-27 首次发布）。目标答案面：**config-source + what-happened**（前两条任务未覆盖的两个面），叠加任务 01 的管道行为。

**v2**（2026-10-28）：只改阅读面，判据一字未动——金样本从「整目录」改为点名到文件（`pom.xml` + `src/main/**`），
并把上下文预算写进卡上（由 `EvalContextBudgetTest` 守）。首采时这张卡的按序读实测
**6.2 万字符**，其中金样本整目录占 60%（含三个金样本自己的测试——它们不是「照写姿势」的必需品）。
v2 已于当日重采（**8/8、一次绿灯是、180 秒、13 条歧义**，见 [baselines](../baselines.md)）。
预算当日二次校准 4.8 → **5.0 万**：同批修复往接入清单与两个问题页补了两条**必需事实**（`ConfigReader` 的四层源链、
失败投影的 kind→status 映射表），必读面一字未减、文档合计 +3.5k 字符——按守卫的口径属「多读的部分值得」，
硬上限 5 万未动（这张卡现在正好压线，下一条事实进来就得先收窄）。

## 目标

在空目录创建一个可运行的 Spring Boot 汇率换算服务：`GET /fx/convert?from=USD&to=CNY&amount=100`，汇率**从配置来**，每次调用**记录观测事件**，并满足下面验收的行为。

## 按序读（只读，仓库内；顺序即执行顺序）

上下文预算：**≤ 5.0 万 Unicode 字符**（`wc -m` 口径，**不是字节**——中文一字约 3 字节；
编号项全文合计、上界口径；机器守卫 `EvalContextBudgetTest`，超了构建即红）。

1. **`docs/INTEGRATION.md` 全文**——接入清单，按执行顺序排；
2. 本任务涉及的六个问题页，**只读「二、机制」与「四、边界」两段**（按段读；预算按全文上界计）：
   `docs/problems/config-source.md`、`docs/problems/what-happened.md`、`docs/problems/who-is-operating.md`、
   `docs/problems/allow-or-not.md`、`docs/problems/failure-response.md`、`docs/problems/api-contract.md`；
3. 金样本 **`examples/spring-boot/pom.xml`** + **`examples/spring-boot/src/main/**`**、
   **`examples/what-happened/pom.xml`** + **`examples/what-happened/src/main/**`**、
   **`examples/config-source/pom.xml`** + **`examples/config-source/src/main/**`**——照写姿势，不要发明。

不得阅读：`eval/reference/**`（参考解）、`eval/runs/**`、`eval/baselines.md`。

需要查状态与边界时再看 `catalog/problems.json` 与 `docs/GLOSSARY.md`；需要断言风格时再看金样本的
`src/test/**`——两者都是**按需**，故意不编号、不计入预算（编号项才是必读面）。

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
