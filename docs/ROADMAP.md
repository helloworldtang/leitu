# 累土 leitu · 路线图

> 更新：2026-09-27。本文是人类视角的进度地图；机器视角见 [catalog/problems.json](../catalog/problems.json)（answered/drafted/open 是唯一权威状态）。
>
> 维护提醒：本页一度停在「0.2.0 待发 / dogfood 待行」，而代码与 README 早已走到 0.2.1 + dogfood 转正——
> 带日期的段落是历史快照（`DocsConsistencyTest` 专门保护，不得改写成史实），**不带日期的段落是当前态，改了事就得当天更新**。

## 在做什么

给人和 AI 的确定性后端地基：把实现不同系统时**反复出现、边界清楚的问题**，沉淀成一套**标准答案**。每条答案 = 五件套（问题页 + 代码 + 守护规则 + 金样本 + 目录条目），CI 机器验收——人拿到确定性，AI 拿到无歧义的生成目标。

## 阶段划分

```
阶段一 结构立库 ──✅──▶ 阶段二 代码答案落地 ──✅──▶ 阶段三 成套交付与实证
（catalog/术语/ADR）      （CRUD 链已闭合）          （当前所在：仅剩 eval 实证）
```

| 阶段 | 内容 | 状态 |
|---|---|---|
| **一 · 结构立库** | 问题目录 20 条立目、术语表+限用词、ADR-001~007、五件套制度 | ✅ 完成（2026-09-07） |
| **二 · 代码答案落地** | 逐条翻 answered：先管道横切层（0.1.0），再 CRUD 主体链（0.2.0） | ✅ 主体完成，见下 |
| **三 · 成套交付与实证** | starter/adapter 层 ✅、API 文档接线 ✅、AI 消费入口 ✅、dogfood ✅ 转正、0.2.1 双通道发布 ✅；**eval 实证 ⬜ 唯一未交付** | 🔄 收尾 |

发布里程碑穿插其间：**0.1.0 已发**（2026-09-13，管道横切层，口径已纠偏为诚实边界）；**0.2.0 / 0.2.1 已发**（2026-09-23，CRUD 链闭合 → BOM 修复 + AI 消费入口）；**npm CLI 已发**（`cli-v0.2.1`，2026-09-24，独立版本线）。

## 历史快照（2026-09-21 · 阶段二～三首批明细）

> 以下为当日的进度快照，保留不改（带日期即历史）。**当前态见下一节。**

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

## 当前态（截至 2026-09-27）

**覆盖度 9 / 20 answered，11 条 open**（数量由机器校验，本文不写死——以 [catalog/problems.json](../catalog/problems.json) 为准）。

阶段三逐项清点：

| 项 | 状态 | 证据 |
|---|---|---|
| starter / adapter 层 | ✅ | 引依赖即用默认装配 / JDBC 真库路径（H2 金测）/ problem+json 投影 |
| AI 消费入口 | ✅ | [skills/leitu/SKILL.md](../skills/leitu/SKILL.md) 路由表 + npm CLI；守门人 `tools/cli/check-skill.mjs` 防漂移 |
| dogfood | ✅ **转正** | agent-platform 已升到 0.2.1；且实测出真缺陷（根 BOM 泄漏 junit-bom，把宿主 JUnit 压回旧版）——已修，并已立守护 |
| 发版 | ✅ 双通道已发 | Maven：`v0.1.0` / `v0.2.0` / `v0.2.1`；npm CLI：`cli-v0.2.1`（独立版本线） |
| **eval 实证** | ⬜ **唯一未交付** | 仅有 1 条任务且基线自认为预演（执行者非全新 agent）——见下 |

### eval 实证为何是主线、缺的到底是什么

[`-P eval`](../.github/workflows/ci.yml) 每次 push 都跑，但它证明的是「**参考解能过**」，不是「**新 agent 能一次过**」——这两件事差的就是阶段三名字里的「实证」。现有基线([eval/baselines.md](../eval/baselines.md))写明：执行者是**已了解仓库的助手**，按任务卡约束完成，属流程贯通与参考解校准，**不作为统计基线**。

所以剩下的是不确定性真正会被测到的那一步：

1. **找一个真正全新的 agent**，在**仓库之外**的工作目录，只给任务卡（[tasks/01-entity-crud-service.md](../eval/tasks/01-entity-crud-service.md)）+ 任务卡列的「允许阅读」清单，**不得阅读 `eval/reference/**`**；
2. 登记四项数字：完成率 / 一次绿灯率 / 时长 / 歧义点清单；
3. **歧义点即工单**——每一个「文档说不清、需要猜」的位置，回头修框架或文档（上一轮预演的五条就是这么被修掉的）；
4. 之后才是规模化（任务集扩到 N 条）与最小上下文规格（≤5 文件的目标目前是**目标**，不是实测结果）。

**验收口径**：一次独立采样完成并登记，即 eval 实证从 ⬜ 转 ✅，阶段三可宣布收官。

## 下一步事项

**0.2.0 发版判定（2026-09-21 记录 · 已了）**：当日对照判据五必答 ✅ / 文档 ✅ / CI ✅ / starter 与真库路径 ✅，仅 dogfood ⬜ 缺位；后 dogfood 于 09-23 完成并实测出 BOM 缺陷，链路随之转正。该判定已落地，留作记录。

**主线路径（当前）**：

1. **eval 实证**（唯一未交付）——全新 agent + 仓库外 + 独立采样，登记四项数字，歧义点转工单。细则见上节。
2. **后续维护**（不阻塞收官）——发布线按需走 tag；覆盖度按真实痛点扩，不为了指标而扩。

**悬置项（等条件成熟，不阻塞主线）**：

- transaction-boundary 验尸裁归属（顺手正名 catalog notes 旧词）
- lock / storage（低频场景，真实痛点再答；设计先例已齐——cache 即模板）
- 覆盖率缺口：11 条 open 按上表口径留在目录里，本页不另行排除
- ~~Maven Central 发布~~ ✅ **已了**：0.2.0 / 0.2.1 已上 Central，配置与三件凭据均已到位；此后只是按 tag 走 `release.yml` + `publish-deployment.yml` 放行
- ~~eval 基准~~ → **已升为主线**，见上节

## 回来时的最小阅读集

1. 本页（进度与去向）
2. [catalog/problems.json](../catalog/problems.json)（机器权威状态）
3. [docs/decisions/](decisions/README.md)（ADR-001~015，每条设计为什么是现在这样）
4. 最近 git log（每条答案一个提交，message 即摘要；阶段三的尾段看 `0d11c9c` AI 消费入口起、`cf505fd` 版本线拆两条止）
