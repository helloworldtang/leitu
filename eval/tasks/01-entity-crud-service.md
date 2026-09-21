# 任务 01 · 最小实体 CRUD 服务

## 目标

在空目录创建一个可运行的 Spring Boot 业务服务：一个实体（字段自定 ≥1）+ 该实体的存取器 + 一个管控动作的守卫 + 两个端点（创建 / 读取）。

## 允许阅读（只读，仓库内）

- README「在业务项目中接入」段
- AGENTS.md、docs/GLOSSARY.md、catalog/problems.json
- docs/problems 下：data-access / allow-or-not / who-is-operating / failure-response / api-contract
- examples/spring-boot（可选对照）

不得阅读：eval/reference/**（参考解）。

## 验收（全过才算完成）

1. `mvn test` 全绿（首跑即绿才计入「一次绿灯」）；
2. 引依赖即用：starter 端口 Bean 就位（上下文 / 判定链 / 观测 / 配置 / 数据工厂 / 文档）；
3. 创建路径：审计盖章（createdBy = 请求操作者）；
4. 守卫路径：非授权主体创建 → 403 + problem+json；
5. 隔离路径：他租户读取 → 业务错交代（problem+json）；
6. 登记：完成率 / 一次绿灯 / 时长 / 歧义点清单。
