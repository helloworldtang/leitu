# 累土 leitu

> 九层之台，起于累土。——《道德经》

**给人和 AI 的确定性后端地基**：把实现不同系统时**反复出现、边界清楚的问题**，沉淀成一套**标准答案**。

人容易理解、拿到确定性；AI 容易理解、用得起来、还能在守护网下安全地迭代这套答案本身。

## 为什么存在

写后端时有三件反复发生的事：

1. **业务代码被框架泡透**——框架注解和 API 散落在业务里，测试要起容器，换底座动不了；
2. **每个项目重解同样的横切问题**——"谁在操作、允许吗、出错怎么返回、发生了什么怎么记"，每个项目一套答案；
3. **AI 生成代码漂移**——当 90% 的代码由 AI 编写，开放题它会猜，每次猜的还不一样。

累土把这三件事的解法固化成标准答案库。每个答案有三个确定性性质：

| 性质 | 含义 |
|---|---|
| **决策坍缩** | 该怎么做只有一个答案（少量 Port + 固定包结构 + 命名规则） |
| **收敛保证** | 今天和明天生成的代码长得一样（默认实现兜底 + 统一 spec） |
| **机验闭环** | 对不对跑一下就知道（边界规则 + 金样本，`./mvnw verify`） |

## 快速开始

三步读懂本项目：

1. 读 [术语表](docs/GLOSSARY.md)——本仓库一切用词的依据；
2. 读 [问题目录](catalog/problems.json)——本库回答哪些问题、各自什么状态；
3. 读一条完整答案：[允许吗（判定链）](docs/problems/allow-or-not.md)——四段式范例，配可运行金样本 `examples/allow-or-not`。

引入依赖（Maven Central 同步后）：

```xml
<dependency>
  <groupId>cn.youhuale</groupId>
  <artifactId>leitu-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

Central 未同步期间的备选：锁 tag 引用（[v0.1.0 tarball](https://github.com/helloworldtang/leitu/archive/refs/tags/v0.1.0.tar.gz)）。**不要引用滚动的 main 分支或 tarball**——升级必须是显式改版本号的决策动作。

## 已交付的答案

| 问题 | 答案一句话 | 入口 |
|---|---|---|
| 这次访问（或出站调用）允许吗？ | 判定链：可插拔 Guards 任一可否决，deny-by-default | [allow-or-not](docs/problems/allow-or-not.md) |
| 谁在操作？代表谁？链路标识？ | 执行上下文：一次绑定处处只读，匿名兜底 | [who-is-operating](docs/problems/who-is-operating.md) |
| 发生了什么，怎么记录？ | 观测事件：一次记录、扇出落点，日志级默认兜底 | [what-happened](docs/problems/what-happened.md) |
| 配置值从哪来？ | 配置源：三源兜底（系统属性 → 环境变量 → leitu.properties）+ SPI 插拔，读时求值 | [config-source](docs/problems/config-source.md) |
| 业务数据怎么读写（审计字段、租户隔离、数据权限）？ | 数据存取器：审计四件套自动盖章＋租户作用域读写＋数据权限走判定链；内存兜底大声标注 | [data-access](docs/problems/data-access.md) |
| 操作失败了，怎么向调用方交代？ | 失败交代：业务错全量、系统错脱敏＋traceId，判定链否决原样随行 | [failure-response](docs/problems/failure-response.md) |
| 怎么缓存（可观测、可替换：进程内 / Redis）？ | 缓存：显式 TTL + LRU 上限的进程内实现，getOrLoad 装载收编（同键只装一次），租户作用域键，观察 opt-in | [cache](docs/problems/cache.md) |
| 访问判定的成套答案 | PermissionPolicy 扩展缝 + 声明式 RBAC-lite + 预算 Guard | capability `access`（[leitu-capability-access](catalog/problems.json)） |

更多问题与状态见[问题目录](catalog/problems.json)（`open` = 已立目待答）。

## 架构

**分层组织（编译期），微内核组装（运行期）：**

```
代码平面                             资产平面（答案的多面表示）
┌────────────────────┐      ┌───────────────────────────┐
│ examples   使用面   │      │ catalog/        机器地图     │
│ starter    装配面   │◀──── │ docs/problems   人的答案页   │
│ adapter    绑定面   │ 五件 │ decisions/      决策链(ADR)  │
│ capability 能力面   │ 套   │ rules           守护规则     │
│ core       端口面   │      │ examples        金样本      │
└────────────────────┘      └───────────────────────────┘
```

- **core** = 执行管道的端口面 + 跨模块协作的调度机制，纯 JDK、零框架依赖（ArchUnit 强制）；
- 累土是**宿主之上的微内核**：生命周期交给宿主（Spring Boot），只承诺能被机器守护的事；
- **答案五件套**：问题页 + 代码 + 守护规则 + 金样本 + 目录条目，缺一不算答案（CI 强制）。

## 导航

- [AGENTS.md](AGENTS.md)——AI 协作入口（最小学习集 / 硬约束 / 贡献答案流程）
- [决策记录](docs/decisions/)——每条设计为什么是现在这样（ADR-001~013）
- [术语表](docs/GLOSSARY.md)——术语与限用词表

## 状态与路线

- **0.1.0（已发布）**：管道横切层——四条答案（who-is-operating / allow-or-not / what-happened + access 能力），每个操作在场的身份 / 放行 / 记录 / 访问判定；
- **0.2.0 开发中**：第五～八条答案已入库——config-source（配置源）、data-access（数据存取器）、failure-response（失败交代）与 cache（缓存：TTL+LRU / getOrLoad 装载收编 / 租户作用域键）——**五条无条件必答全部 answered**，151 个测试全绿；
- **API 文档已定策（drafted）**：不自研，集成 springdoc + knife4j（OpenAPI 标准）——实现随 starter 层开启落地（ADR-013）；
- **未覆盖**：锁 / 存储，见[问题目录](catalog/problems.json)——引入前先确认你的缺口不在其中；
- 下一程：starter 层规划（API 文档接线）→ 真实项目接入验证；
- 发布节奏：版本一律走 tag（已发 `v0.1.0`）；不承诺 main 分支稳定。

## 坐标与许可

`cn.youhuale:leitu-*` · Java 21 · [MIT](LICENSE) · 文档站：[leitu 文档](docs/index.md)

---

*人和 AI 共同使用、共同迭代的标准答案库——越用越厚，越用越稳。*
