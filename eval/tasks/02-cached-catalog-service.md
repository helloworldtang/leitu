# 任务 02 · 带缓存的图书目录服务

任务卡版本：**v1.1**（2026-10-26）。v1 已采样；v1.1 唯一变化：把验收 2 引用的 Bean 清单内联（判据零变化）。
目标答案面：data-access + **cache** + failure-response + api-contract（在任务 01 的管道之上，多压一层缓存语义）。

**v2**（2026-10-28）：只改阅读面，判据一字未动——金样本从「整目录」改为点名到文件（`pom.xml` + `src/main/**`），
并把上下文预算写进卡上（由 `EvalContextBudgetTest` 守）。整目录会把金样本自己的测试
也算进必读，而「照写姿势」要的是 main 里的写法、不是它的断言风格。**v2 与 v1.1 的读数不可直接比较**。
预算当日二次校准 4.7 → **4.9 万**：同批修复往接入清单与两个问题页里补了两条**必需事实**
（`ConfigReader` Bean 的四层源链、失败投影的 kind→status 映射表），必读面一字未减、文档合计 +3k 字符；
按守卫的口径这属于「多读的部分值得」，不是默默调大（硬上限 5 万未动）。

## 目标

在空目录创建一个可运行的 Spring Boot 业务服务：图书实体（业务字段 ≥2）+ JDBC 存取 + 创建 / 读取两个端点，**读取走缓存**，并满足下面验收的行为。

## 按序读（只读，仓库内；顺序即执行顺序）

上下文预算：**≤ 4.9 万 Unicode 字符**（`wc -m` 口径，**不是字节**——中文一字约 3 字节；
编号项全文合计、上界口径；机器守卫 `EvalContextBudgetTest`，超了构建即红）。

1. **`docs/INTEGRATION.md` 全文**——接入清单，按执行顺序排；
2. 本任务涉及的六个问题页，**只读「二、机制」与「四、边界」两段**（按段读；预算按全文上界计）：
   `docs/problems/data-access.md`、`docs/problems/cache.md`、`docs/problems/allow-or-not.md`、
   `docs/problems/who-is-operating.md`、`docs/problems/failure-response.md`、`docs/problems/api-contract.md`；
3. 金样本 **`examples/spring-boot/pom.xml`** + **`examples/spring-boot/src/main/**`** 与
   **`examples/cache/pom.xml`** + **`examples/cache/src/main/**`**——照写姿势，不要发明。

不得阅读：`eval/reference/**`（参考解）、`eval/runs/**`、`eval/baselines.md`。

需要查状态与边界时再看 `catalog/problems.json` 与 `docs/GLOSSARY.md`；需要断言风格时再看金样本的
`src/test/**`——两者都是**按需**，故意不编号、不计入预算（编号项才是必读面）。

## 验收（全过才算完成）

1. `mvn test` 全绿，且**第 3～7 条行为各有至少一个测试断言**——零测试时 surefire 也会绿，那种绿不计入「一次绿灯」；
2. 引依赖即用：下列 Bean 在上下文里就位（按类型取即可，不必知实现）——
   `ExecutionContextBinder` / `ExecutionContextReader` / `GuardChain` / `ConfigReader` /
   `ObservationRecorder` / `CachePolicy` / `JdbcDataStoreFactory` / `OpenAPI`（末者需自引 springdoc）；
   `Cache` 实例**不在其中**——它按用途命名、装配处显式分支，由你自己声明（见 INTEGRATION 与 cache 问题页）；
3. 创建路径：审计盖章（createdBy = 请求操作者）+ 业务列落库（JDBC 六列表 + 业务列）；
4. **缓存命中**：同一 id 连续读取两次，底层数据源只被触达**一次**——用计数装饰器或观测事件（`cache.hit` / `cache.miss`）给出证据；
5. **缓存键租户作用域**：租户 A 创建的数据，租户 B 读同一 id 读不到（且不得读到 A 的副本）；
6. 失败投影：读不到 → 业务错 problem+json（服务内统一一种状态码）；判定否决 → 403 `urn:leitu:problem:guard.denied`；
7. `/v3/api-docs` 中两个 operationId（`book.create` / `book.get`）均在文档中；
8. 登记：完成率 / 一次绿灯 / 时长 / 歧义点清单。

## 读数口径（与任务 01 一致）

- 完成率：验收逐条自评，能自证给命令，不能自证明说未验证；
- 一次绿灯：首次 `mvn test` 是否全绿；
- 时长：`date +%s` 自任务开始至首次测试结束；
- 歧义点：每次「文档没说清、我不得不猜」记一条（`位置 | 我猜了什么 | 依据`）。
