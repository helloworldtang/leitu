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

## 在业务项目中接入

一个 Spring Boot 业务项目，两处改动即可用起来（完整范例：[examples/spring-boot](examples/spring-boot)）：

两个前置，缺一个都会在编译期踩坑：

1. **JDK 21**——leitu 以 release 21 编译发布，更低版本的 JVM 会直接 `UnsupportedClassVersionError`；
2. **`maven.compiler.release=21` 必须自己写**——BOM import 只接管依赖版本，**不继承 properties**；
   不写的话 maven-compiler-plugin 按默认低版本编译，JDK 21 下会报 `找不到符号: 类 var`：

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
</properties>
```

先在 dependencyManagement 里引入 leitu-parent（BOM）——**一次引入，八个模块都不用写版本号**
（手写版本号是未来某次升级事故的起点；哪天想 pin 版本，只改这一处）：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>cn.youhuale</groupId>
      <artifactId>leitu-parent</artifactId>
      <version>0.2.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

> 引入姿势两种都行。**推荐 import BOM**——Spring Boot 项目的 parent 位通常已被 `spring-boot-starter-parent` 占用。
> 用 `<parent>` 继承（0.2.1 起可用）会顺带继承 `maven.compiler.release=21` 与 enforcer 的 JDK 门禁；
> 0.2.0 的 parent 姿势不可用（BOM 里自有模块版本写的 `${project.version}` 被继承时在使用者项目里求值，
> 实测报 `Could not find artifact cn.youhuale:leitu-core:jar:<你的项目版本>`）。

再按需引依赖（版本号可省略）：

```xml
<dependency>
  <groupId>cn.youhuale</groupId>
  <artifactId>leitu-spring-boot-starter</artifactId>
</dependency>

<!-- 把 leitu 的架构规则接到自己的工程里：import 之后同样不用写版本号 -->
<dependency>
  <groupId>cn.youhuale</groupId>
  <artifactId>leitu-rules</artifactId>
  <scope>test</scope>
</dependency>
```

```java
// 装配：存取器一行声明；守卫声明即收编——其余全部自动（用户 Bean 优先）
@Bean
DataStore<Order, String> orders(JdbcDataStoreFactory factory) {
    return factory.create(ORDER_MAPPING);   // 显式映射：表 / 列 / 行⇄实体
}

@Bean
Guard orderCreateGuard() {
    return request -> "order.create".equals(request.action()) && !"admin".equals(request.subject())
            ? Decision.deny("仅管理员可创建订单", Retry.never())
            : Decision.allow();
}
```

引依赖即得：执行上下文（trace 贯穿）/ 判定链 / 观测 / 配置 / problem+json 失败投影 / `/v3/api-docs` 文档端点。接入要点（均可对照 [examples/spring-boot](examples/spring-boot)）：

- **依赖**：Web + JDBC 用标准 Spring 组合（`spring-boot-starter-web` / `spring-boot-starter-jdbc` + 驱动）；控制器方法参数名依赖 `-parameters` 编译标志（Spring 应用标准配置）；
- **入口**：在请求入口绑定操作者（照范例 Filter 写法，接入你的认证体系）；
- **表约定**：存取器表含 `tenant / id / created_by / created_at / updated_by / updated_at` 六列（见 [data-access 问题页](docs/problems/data-access.md)）。

**发布状态**：`0.2.1` 已发布到 Maven Central（groupId `cn.youhuale`），直接用上面的坐标引用即可，无需本地 install。**不要引用滚动的 main 分支**——升级必须是显式改版本号的决策动作。

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
| 服务对外暴露了什么？ | springdoc + knife4j 接线：problem+json / traceId / 动作同源 / 端点豁免——引 starter 即得 | [api-contract](docs/problems/api-contract.md) |

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

## 给 AI 的入口（skill + CLI）

README:7 承诺「AI 容易理解、用得起来」——落点在这里（`AGENTS.md` 只在仓库内生效，AI 在业务项目里看不到它）：

- **Agent skill**：[skills/leitu/SKILL.md](skills/leitu/SKILL.md)——路由表形态（触发场景 → 答案 → 文档/金样本），装进你的 AI（Claude Code / WorkBuddy 等）；AI 在业务项目里遇到表内问题会先查 leitu，而不是手写一次性实现。
- **CLI**：包名 `leitu-cli`（npm），命令名 `leitu`——`npx leitu-cli list --json` / `npx leitu-cli explain <id> --json`，或 `npm i -g leitu-cli` 后直接 `leitu list`。零依赖、只读、机器可读（源码 [tools/cli](tools/cli)，不进 Maven reactor）；`--all` 含 open 条目。
- **守门**：`node tools/cli/check-skill.mjs`——SKILL.md 引用路径失效、answered 条目被路由漏掉、npm 快照与 catalog 不一致，任何一条即红灯（反向自测过）。

## 导航

- [AGENTS.md](AGENTS.md)——AI 协作入口（最小学习集 / 硬约束 / 贡献答案流程）
- [路线图](docs/ROADMAP.md)——阶段、进度与下一步（人类视角；机器视角见问题目录）
- [决策记录](docs/decisions/)——每条设计为什么是现在这样（ADR-001~015；CI 校验本文档写的上限与目录里的实际编号一致）
- [术语表](docs/GLOSSARY.md)——术语与限用词表

## 状态与路线

- **0.1.0（已发布）**：管道横切层——四条答案（who-is-operating / allow-or-not / what-happened + access 能力），每个操作在场的身份 / 放行 / 记录 / 访问判定；
- **0.2.0（已发布）**：第五～九条答案已入库（config-source / data-access / failure-response / cache / api-contract）+ **starter / adapter 层首批落地**——引依赖即用的默认装配、JDBC 真库路径、problem+json 投影——**五条无条件必答全部 answered**，测试全绿（数量由 CI 断言，文档不写死——写死的第二天就开始漂）；
- **API 文档已落地（answered）**：springdoc + knife4j 接线随 starter 交付（ADR-013 四条增值点）；
- **未覆盖**：锁 / 存储，见[问题目录](catalog/problems.json)——引入前先确认你的缺口不在其中；
- 下一程：0.2.1 转正 dogfood 分支（agent-platform 真实接入）；
- 发布节奏：版本一律走 tag（已发 `v0.1.0`）；不承诺 main 分支稳定。

## 坐标与许可

`cn.youhuale:leitu-*` · Java 21 · [MIT](LICENSE) · 文档站：[leitu 文档](docs/index.md)

---

*人和 AI 共同使用、共同迭代的标准答案库——越用越厚，越用越稳。*
