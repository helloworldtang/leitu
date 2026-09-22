# 累土 leitu · 路线图

> 更新：2026-09-21。本文是人类视角的进度地图；机器视角见 [catalog/problems.json](../catalog/problems.json)（answered/drafted/open 是唯一权威状态）。

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
| **三 · 成套交付与实证** | starter/adapter 层（引依赖即用的默认装配）✅ 首批落地、API 文档接线 ✅；dogfood + eval 基准 ⬜ 待行 | 🔄 进行中 |

发布里程碑穿插其间：**0.1.0 已发**（2026-09-13，管道横切层，口径已纠偏为诚实边界）；**0.2.0 待发**（见下）。

## 阶段二 · 三进度明细（截至 2026-09-21）

**answered = 9，drafted = 0，测试全绿**（数量由 CI 校验，文档不写死）（仓库 [helloworldtang/leitu](https://github.com/helloworldtang/leitu)，CI 绿，开发版 0.2.0-SNAPSHOT）：

| 环节 | 答案 | 模块 | 交付日 |
|---|---|---|---|
| 业务横切（0.1.0 已发） | who-is-operating / allow-or-not / what-happened + access | leitu-core + capability-access | 09-07~13 |
| 配置中心 | config-source（三源兜底 + SPI 插拔 + 值静默教义） | leitu-core | 09-13 |
| 数据库 | data-access（审计四件套自动盖章 + 租户作用域 + 数据权限走判定链） | leitu-capability-data | 09-13 |
| 失败交代 | failure-response（业务错全量 / 系统错脱敏，五条无条件必答就此全清） | leitu-core | 09-13 |
| 缓存 | cache（TTL+LRU / getOrLoad 装载收编 / 租户作用域键） | leitu-capability-cache | 09-13 |
| 装配 / 绑定层（阶段三首批） | api-contract **answered**；starter / adapter 三模块落地（默认装配 / JDBC 真库路径 / problem+json 投影） | 新三模块 | 09-21 |

**CRUD 链闭合**：业务横切 ✅ → 配置 ✅ → 数据库 ✅ → 缓存 ✅ → API 文档 ✅（starter 层已开启）。

**阶段三首批（2026-09-21）**：starter / adapter 层落地——引依赖即用的默认装配、JDBC 真库路径（H2 金测试）、problem+json 失败投影与文档接线；api-contract drafted → answered。

## 下一步事项

**0.2.0 发版判定：待复核（2026-09-21 更新）**——对照判据：五必答 ✅ / 文档 ✅ / CI ✅ / **starter 与真库路径 ✅（本日落地）**；仅 **dogfood ⬜** 缺位。口径：dogfood 过（或按内容定版本号）再发；tag 晚打无损。

**主线路径（建议顺序）**：

1. **dogfood**——真实项目引入一条链路，验证「可以拿去用」不是纸面判断（agent-platform 是既定候选）。
2. **发版**——dogfood 过（或按内容定版本号）再发；版本号按内容定，Release 口径照 0.1.0 教训逐条对照交付物。

**悬置项（等条件成熟，不阻塞主线）**：

- transaction-boundary 验尸裁归属（顺手正名 catalog notes 旧词）
- lock / storage（低频场景，真实痛点再答；设计先例已齐——cache 即模板）
- Maven Central 发布（配置就绪，等三件用户凭据：Central 账号+DNS TXT / User Token / GPG）
- eval 基准（种子已立：eval/ 协议 + 首条任务 + 基线；规模化与最小上下文规格待行）

## 回来时的最小阅读集

1. 本页（进度与去向）
2. [catalog/problems.json](../catalog/problems.json)（机器权威状态）
3. [docs/decisions/](decisions/README.md)（ADR-001~013，每条设计为什么是现在这样）
4. 最近 git log（每条答案一个提交，message 即摘要）
