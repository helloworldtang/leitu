# AGENTS.md —— AI 协作入口

## 这是什么项目

累土（leitu）：给人和 AI 的确定性后端地基。把实现不同系统时反复出现、边界清楚的问题，沉淀成一套标准答案。

- 愿景一句话：人和 AI 共同使用、共同迭代的标准答案库——越用越厚，越用越稳。
- 技术栈：Java 21；宿主 Spring Boot（core 层零框架依赖）。
- 当前阶段：**0.2.1 已发布**，双通道——Maven Central（`cn.youhuale:*` 九坐标）与 npm（`leitu-cli`，命令 `leitu`）。第五～九条答案已入库（配置来源 / 数据访问 / 失败交代 / 缓存 / API 文档），五条无条件必答全部 answered；**starter / adapter 层已落地**（引依赖即用 / JDBC 真库路径 / problem+json 投影，ADR-014 / ADR-015）；锁 / 存储等未覆盖，见 catalog `open` 条目（`open/drafted` 是正常态）。

## 最小学习集（按序：四个必读 + 按需入口）

1. [docs/GLOSSARY.md](docs/GLOSSARY.md) —— 术语表，一切用词的依据
2. [catalog/problems.json](catalog/problems.json) —— 问题目录：本库回答哪些问题、各自什么状态
3. [docs/problems/allow-or-not.md](docs/problems/allow-or-not.md) —— 问题页的标准四段式范例（为什么/机制/取舍/边界）
4. [docs/decisions/README.md](docs/decisions/README.md) —— 决策索引：每条设计为什么是现在这样
5. 按需：动 core 设计时，再读 ADR-002 / ADR-003 / ADR-005；动 starter / adapter 层读 ADR-014 / ADR-015

> 注意区分两件事：上面是**学本项目**的最小集；**写一个能跑的服务**（接入 leitu）走 [docs/INTEGRATION.md](docs/INTEGRATION.md)，它按执行顺序排，别从这份清单开始。

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
起步用 `java scripts/NewAnswer.java <id> "问题一句话"`——生成四段式骨架 + 同步点清单（含守卫标注），重复立目 / 覆盖已有页会拒绝；它不改 catalog、不改根 pom，那些仍需过人。

## 成熟度（这个项目自己有多「AI 可用」）

[MATURITY.md](MATURITY.md) —— 六维五级模型（可发现 / 可理解 / 可验证 / 可执行 / 可演进 / 可信赖）
+ 本仓库自评与差距清单。**不看总分，看卡在哪一级的门槛**（打分会被优化成 KPI，缺口会被高分掩盖）。

当前自评 **L3（有反馈）**——L4 的机制多数已备，但**覆盖不够不判 L4**：漂移守卫只守住三处，
而最贵的那类（答案页声明了已交付却未描述的装配）已犯两次仍无守。按模型自己的规矩，
某一维不达标就停在该级，不许用别维的高分补。升级条件与两条待补守卫写在文中。

## 验证

- **唯一构建入口：`./mvnw verify`**（编译 + 测试 + 边界规则 + **catalog 完整性 + markdown 链接检查**）。机器依赖只有 JDK 21——版本不对会被 enforcer 带着修复说明拦下。
- **answered 的宣告权在机器**：`CatalogIntegrityTest`（leitu-rules）校验五件套真实存在、枚举合法、依赖图无孤儿、全仓相对链接有效——不满足的 answered/drafted 会让构建红灯。
- 架构状态导出：`java scripts/ExportArchitecture.java`（JDK 单文件直接运行，无其他依赖；catalog 变更后跑，catalog → 4R 四图：Rank 双平面 / Role 职责 / Relation 依赖图 / Rule 规则清单；CI 侧另有 staleness 检查）。
- **发布通道（两条版本线，互不影响，不要互相绑定）**：① **leitu 库——有语义的那条线**：`v<x.y.z>` tag → `release.yml` 上传 Maven Central（`autoPublish=false`，停在 VALIDATED，还要用 `publish-deployment.yml` 走官方 API 放行）。版本真源 = 根 pom 的 `<version>`，由 `skills/leitu/SKILL.md` 承载给 AI（新项目照抄该版本、老项目保持既有版本），守门人 `tools/cli/check-skill.mjs` 强制两者相等（CI 每次 push 都跑）；发布时刻另有版本闸门 `scripts/check-release-version.sh`（tag 必须等于 pom 版本且非 SNAPSHOT，否则当场拦下——版本发了就不可撤回）。② **npm CLI——无状态工具，版本无兼容性承诺**：`cli-v<x.y.z>` tag → `npm-release.yml`（上传前先跑 `tools/cli/preflight.mjs --tag-prefix=cli-v` 自检，成功后自动建 Release 并复核 registry 可见）；用的人一律 `npx leitu-cli@latest`，所以它不必等库版本，改了 catalog 快照就顺手发一次。把 CLI 绑到库版本只会让「想给 CLI 修个 bug」变成「必须动库版本」。**改 npm-release.yml 后用 `workflow_dispatch` + `dry_run=true` 试跑**（三条流水线里只有它有干跑开关）；release.yml / publish-deployment.yml 没有干跑——release.yml 手动 dispatch 会跳过 tag 比对、拿当前 pom 版本直接真发布，改动后靠 push CI 验证，别拿真版本去试。

## 评测与工具

- `eval/`：AI 生产力基准（**种子已立**：协议 + 三张任务卡各带 no-leitu 对照组 + 参考解 + 三轮基线记录）——标准任务集 + 完成率/时长/一次绿灯率；规模化跑批待行。参考解已进 CI：`ci.yml` 用 `./mvnw -B -P eval verify` 强制它参与构建（参考解红灯 = push 被挡）
- 最小上下文规格：分两件事——**学会本项目**：上面那个最小学习集；**写出第一个答案**：任务卡的「按序读」，
  按**字符预算**记（不按文件数：一条答案一页问题页是正确的，按文件数记账等于惩罚分页），预算写在卡上、
  由 `EvalContextBudgetTest` 守（目录即红 / 超预算即红 / 无声明即红）
- AI 消费入口：`skills/leitu/SKILL.md`（对外路由表）+ `tools/cli`（`leitu list/explain`，零依赖只读，**不进 Maven reactor**、不参与 `./mvnw verify`）。守门人 `node tools/cli/check-skill.mjs` 四条断言——SKILL.md 引用路径 / answered 条目覆盖 / npm 快照 / **SKILL.md 里的库版本 == 根 pom 的 `<version>`**，任何一条漂移即红（也接在 CI 最前面）。改 catalog 或改版本后跑一遍。**CLI 自己的版本号不在此约束内**——它是无状态工具，用的人一律 `npx leitu-cli@latest`，与库版本各走各的 tag。
