# AGENTS.md —— AI 协作入口

## 这是什么项目

累土（leitu）：给人和 AI 的确定性后端地基。把实现不同系统时反复出现、边界清楚的问题，沉淀成一套标准答案。

- 愿景一句话：人和 AI 共同使用、共同迭代的标准答案库——越用越厚，越用越稳。
- 技术栈：Java 21；宿主 Spring Boot（core 层零框架依赖）。
- 当前阶段：**0.1.0——管道横切层可用**。已交付四条答案（识别 / 放行 / 记录 / 访问判定）；业务开发主体链路（配置来源 / 数据访问 / 失败交代 / 缓存 / API 文档等）未覆盖，见 catalog `open` 条目（`open/drafted` 是正常态）。

## 最小学习集（按序，≤5 个文件）

1. [docs/GLOSSARY.md](docs/GLOSSARY.md) —— 术语表，一切用词的依据
2. [catalog/problems.json](catalog/problems.json) —— 问题目录：本库回答哪些问题、各自什么状态
3. [docs/problems/allow-or-not.md](docs/problems/allow-or-not.md) —— 问题页的标准四段式范例（为什么/机制/取舍/边界）
4. [docs/decisions/README.md](docs/decisions/README.md) —— 决策索引：每条设计为什么是现在这样
5. 需要动 core 设计时，再读 ADR-002 / ADR-003 / ADR-005

## 硬约束

- 术语以 [GLOSSARY](docs/GLOSSARY.md) 为准；限用词（契约 / kernel / runtime）按其限定使用，见限用词表。
- 本仓库自包含：不引入任何未在本仓库定义的外部私有概念；设计论证只依据第一性原理与业界公开实践。
- 新术语入库过三关：唯一指称 / 业界对齐 / 不携带无法兑现的承诺。
- 承诺纪律：框架只承诺能被机器守护的事（词能兑现、目标能被测试守住、差异经得起对标），见 ADR-004。

## 如何贡献一条答案（五件套流程）

一个问题要算「已入库」，五件缺一不可（CI 将强制检查）：

| 件 | 载体 | 给谁 |
|---|---|---|
| 问题页 | `docs/problems/<id>.md` | 人 |
| 代码 | core 或 capability | 人 + AI + 运行时 |
| 守护规则 | rules | 机器 |
| 金样本 | `examples/<id>/` | AI 学习 + 金丝雀 |
| 目录条目 | `catalog/problems.json` | AI 地图 + 覆盖度 |

新增问题走：三法推导（场景枚举 / 真实痛点 / 漂移成本）→ 三准入测试（管道 / 必然 / 形状）→ 定档（无条件必答 / 条件必答 / open）→ 三段交付（形状 + 安全兜底 + 成套答案）。制度详见 ADR-006。

## 验证

- **唯一构建入口：`./mvnw verify`**（编译 + 测试 + 边界规则 + **catalog 完整性 + markdown 链接检查**）。机器依赖只有 JDK 21——版本不对会被 enforcer 带着修复说明拦下。
- **answered 的宣告权在机器**：`CatalogIntegrityTest`（leitu-rules）校验五件套真实存在、枚举合法、依赖图无孤儿、全仓相对链接有效——不满足的 answered/drafted 会让构建红灯。
- 架构状态导出：`java scripts/ExportArchitecture.java`（JDK 单文件直接运行，无其他依赖；catalog 变更后跑，再生成 4R 视图；CI 侧另有 staleness 检查）。

## 评测与工具（占位，规划中）

- `eval/`：AI 生产力基准——标准任务集 + 完成率/时长/一次绿灯率（证明"确定性 foundation"主张的实证义务）
- 最小上下文规格：一个全新 agent 学会本项目、正确写出第一个答案所需的最小阅读集（目标 ≤5 文件）
- 架构状态导出：catalog → 4R 四图（Rank 双平面 / Role 职责 / Relation 依赖图 / Rule 规则清单），入口 `java scripts/ExportArchitecture.java`
