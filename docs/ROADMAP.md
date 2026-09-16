# 累土 leitu · 路线图

> 更新：2026-09-17。本文是人类视角的进度地图；机器视角见 [catalog/problems.json](../catalog/problems.json)（answered/drafted/open 是唯一权威状态）。

## 在做什么

给人和 AI 的确定性后端地基：把实现不同系统时**反复出现、边界清楚的问题**，沉淀成一套**标准答案**。每条答案 = 五件套（问题页 + 代码 + 守护规则 + 金样本 + 目录条目），CI 机器验收——人拿到确定性，AI 拿到无歧义的生成目标。

## 阶段划分

```
阶段一 结构立库 ──✅──▶ 阶段二 代码答案落地 ──▶ 阶段三 成套交付与实证
（catalog/术语/ADR）      （当前所在：CRUD 链已闭合）   （starter/adapter + eval）
```

| 阶段 | 内容 | 状态 |
|---|---|---|
| **一 · 结构立库** | 问题目录 20 条立目、术语表+限用词、ADR-001~007、五件套制度 | ✅ 完成（2026-09-07） |
| **二 · 代码答案落地** | 逐条翻 answered：先管道横切层（0.1.0），再 CRUD 主体链（0.2.0） | ✅ 主体完成，见下 |
| **三 · 成套交付与实证** | starter/adapter 层（引依赖即用的默认装配）+ API 文档接线 + dogfood + eval 基准 | ⬜ 未开启（下一个大阶段） |

发布里程碑穿插其间：**0.1.0 已发**（2026-09-13，管道横切层，口径已纠偏为诚实边界）；**0.2.0 待发**（见下）。

## 阶段二进度明细（截至 2026-09-17）

**answered = 8，drafted = 1，151 测试全绿**（仓库 [helloworldtang/leitu](https://github.com/helloworldtang/leitu)，CI 绿，开发版 0.2.0-SNAPSHOT）：

| 环节 | 答案 | 模块 | 交付日 |
|---|---|---|---|
| 业务横切（0.1.0 已发） | who-is-operating / allow-or-not / what-happened + access | leitu-core + capability-access | 09-07~13 |
| 配置中心 | config-source（三源兜底 + SPI 插拔 + 值静默教义） | leitu-core | 09-13 |
| 数据库 | data-access（审计四件套自动盖章 + 租户作用域 + 数据权限走判定链） | leitu-capability-data | 09-13 |
| 失败交代 | failure-response（业务错全量 / 系统错脱敏，五条无条件必答就此全清） | leitu-core | 09-13 |
| 缓存 | cache（TTL+LRU / getOrLoad 装载收编 / 租户作用域键） | leitu-capability-cache | 09-13 |
| API 文档 | api-contract **drafted**——已定策不自研，集成 springdoc + knife4j（ADR-013） | （随 starter 层落地） | 09-13 定策 |

**CRUD 链闭合**：业务横切 ✅ → 配置 ✅ → 数据库 ✅ → 缓存 ✅ → API 文档（定策 ✅ / 实现 ⏳ starter 层）。

## 下一步事项

**0.2.0 发版判定：暂缓（2026-09-17 复核）**——对照判据（用户 0.1.0 时代自定）：五必答 ✅ / 文档 ✅ / CI ✅，但 **dogfood ❌**；更实质的：data-access / cache 只有内存兜底（无真库 adapter），无 starter 层（无「引依赖即用」），Central 坐标不可达——**发版目的是让人用起来，此目的当前不成立**。故事完整 ≠ 值得占用版本号；tag 晚打无损。

**主线路径（建议顺序）**：

1. **starter 层规划（阶段三开启）**——第一个 Spring 模块，是整套架构决策（宿主版本管理 / BOM / 模块布局）：springdoc+knife4j 接线（api-contract drafted → answered）+ 各能力默认装配（三段交付的第三段「引依赖即用」）+ 至少一条真库路径。
2. **dogfood**——真实项目引入一条链路，验证「可以拿去用」不是纸面判断（agent-platform 是既定候选）。
3. **发版**——starter/真库路径通了（或至少 dogfood 过）再发；届时版本号按内容定（0.2.0 或 0.3.0），Release 口径照 0.1.0 教训逐条对照交付物。

**悬置项（等条件成熟，不阻塞主线）**：

- transaction-boundary 验尸裁归属（顺手正名 catalog notes 旧词）
- lock / storage（低频场景，真实痛点再答；设计先例已齐——cache 即模板）
- Maven Central 发布（配置就绪，等三件用户凭据：Central 账号+DNS TXT / User Token / GPG）
- eval 基准 + 最小上下文规格（阶段三的实证义务）

## 回来时的最小阅读集

1. 本页（进度与去向）
2. [catalog/problems.json](../catalog/problems.json)（机器权威状态）
3. [docs/decisions/](decisions/README.md)（ADR-001~013，每条设计为什么是现在这样）
4. 最近 git log（每条答案一个提交，message 即摘要）
