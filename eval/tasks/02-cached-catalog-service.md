# 任务 02 · 带缓存的图书目录服务

任务卡版本：**v1.1**（2026-10-26）。v1 已采样；v1.1 唯一变化：把验收 2 引用的 Bean 清单内联（判据零变化）。
目标答案面：data-access + **cache** + failure-response + api-contract（在任务 01 的管道之上，多压一层缓存语义）。

## 目标

在空目录创建一个可运行的 Spring Boot 业务服务：图书实体（业务字段 ≥2）+ JDBC 存取 + 创建 / 读取两个端点，**读取走缓存**，并满足下面验收的行为。

## 按序读（只读，仓库内；顺序即执行顺序）

1. **`docs/INTEGRATION.md` 全文**——接入清单，按执行顺序排；
2. 本任务涉及的六个问题页，**只读「二、机制」与「四、边界」两段**：
   data-access / **cache** / allow-or-not / who-is-operating / failure-response / api-contract；
3. `examples/spring-boot` 与 `examples/cache` **整目录**——照写姿势，不要发明；
4. 需要查状态与边界时再看 `catalog/problems.json`、`docs/GLOSSARY.md`。

不得阅读：`eval/reference/**`（参考解）、`eval/runs/**`、`eval/baselines.md`。

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
