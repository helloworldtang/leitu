# 任务 01 · 最小实体 CRUD 服务

任务卡版本：**v2**（2026-10-26）。改动只有一条：把「允许阅读」的无序清单换成「按序读 4 处」——
上一版（v1）的 20 个歧义点里 15 个是「文档写了但没找到」，任务卡本身没给阅读顺序是直接原因。
**跨版本数字不可直接比较**：v1 的 6/6、一次绿灯、5 分 33 秒是旧判据下的读数，
v2 要重新采一次，再与 v1 并列登记（见 [baselines.md](../baselines.md)）。

## 目标

在空目录创建一个可运行的 Spring Boot 业务服务：一个实体（字段自定 ≥1）+ 该实体的存取器 + 一个管控动作的守卫 + 两个端点（创建 / 读取）。

## 按序读（只读，仓库内；顺序即执行顺序）

1. **`docs/INTEGRATION.md` 全文**——接入清单，按执行顺序排（前置 / 依赖 / 属性 / 建表 / 绑定 / 存取器 / 投影 / 自检）；
2. 本任务涉及的五个问题页，**只读「二、机制」与「四、边界」两段**：
   data-access / allow-or-not / who-is-operating / failure-response / api-contract；
3. `examples/spring-boot` **整个目录**——重点 `pom.xml` / `schema.sql` / `DemoContextFilter` / `DemoConfig` / `OrderController`；
   照写姿势，不要发明（上一版只放开了两个文件，结果映射对象与建表都没处可查）；
4. 需要查状态与边界时再看 `catalog/problems.json`、`docs/GLOSSARY.md`。

不得阅读：eval/reference/**（参考解）。

> 为什么不再随手翻：同一件接入事实曾散在三处，AI 的失败模式不是读不懂，是读漏。
> 读完之后如果仍然要猜，把那条**猜的位置**记进歧义点清单——那是这一页的工单，不是你的失误。

## 验收（全过才算完成）

1. `mvn test` 全绿，且**至少一个测试断言下面第 3～5 条的行为**——零测试时 surefire 也会绿，那种绿不计入「一次绿灯」；
2. 引依赖即用：下列 Bean 在上下文里就位（按类型取即可，不必知实现）——
   `ExecutionContextBinder` / `ExecutionContextReader` / `GuardChain` / `ConfigReader` /
   `ObservationRecorder` / `CachePolicy` / `JdbcDataStoreFactory`（后者来自 adapter-jdbc；
   引了 springdoc 时另有 `OpenAPI`）；
3. 创建路径：审计盖章（createdBy = 请求操作者）；
4. 守卫路径：非授权主体创建 → 403 + problem+json，响应体里的 `type` 是 `urn:leitu:problem:guard.denied`；
5. 隔离路径：他租户读取 → 业务错交代（problem+json，非 200 非 500）；
6. 登记：完成率 / 一次绿灯 / 时长 / 歧义点清单。
