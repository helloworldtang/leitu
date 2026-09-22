# 允许吗（allow-or-not）

> catalog：pipeline ｜ required: always ｜ status: answered（stability: frozen，since 0.1.0） ｜ depends_on: who-is-operating

## 一、为什么

每个后端操作在放行时刻都要被审视，而能说"不"的来源远不止权限：

| 说"不"的来源 | 说的是什么 | 重试语义 |
|---|---|---|
| 功能权限 | 这个角色不能做这事 | 永久否决 |
| 数据权限 | 这条数据不归你看 | 永久否决 |
| 限流 | 请求太密了 | 稍后重试 |
| 配额 | 额度用完了 | 到期重试 |
| 熔断 | 下游扛不住（出站方向） | 稍后重试 |
| 维护窗口 / 风控 | 现在不行的各种理由 | 各异 |

它们发生在管道的**同一时刻**（放行检查），产出**同一形状的答案**，理应走**同一条错误通道**。

没有标准答案时的漂移成本：每个项目自造拦截器——鉴权/限流/熔断三套写法、三种错误结构、三个配置面；AI 生成时漂移更甚（每一处横切都要猜）。

## 二、机制（标准答案的形状）

**判定链**：一串可插拔 Guard，任一可否决，输出统一 Decision。

```java
// 已实现：leitu-core 的 cn.youhuale.leitu.core.guard 包
public interface Guard {
    Decision check(AccessRequest request);
}

public record Decision(Verdict verdict, String reason, Retry retry) {
    // verdict: allow | deny
    // reason: 为什么否决（错误即教程：必须告诉调用方怎么办）
    // retry:  none | later | at
}
```

- **双执行点，同一协议**：入口判定链（进来的请求：权限/限流/风控）；出站判定链（调出去的请求：熔断/预算）
- **安全默认**：deny-by-default，显式放行——宁可起步多配一行，不可默认裸奔
- **兜底**：未装配任何 Guard 时，安全维度的默认答案是否决（分维度默认值，见 GLOSSARY）
- **成套答案**：`leitu-capability-access`（已交付 0.1.0）——`PermissionPolicy` 扩展缝（换模型零改业务）+ 声明式 RBAC-lite（`RoleMap` 一张表查全部真相）+ 预算 Guard（`BudgetSpec`，耗尽带重试语义）参考实现；限流/配额沿扩展缝接入

## 三、取舍

**为什么不是窄的权限接口（AuthorizationChecker 式）**：权限只是说"不"的来源之一。统一协议后，限流/配额/熔断共享同一拦截、观测与错误通道——一个问题的答案，而不是四个平行机制。

**为什么限流并入判定链（而非独立问题）**：同一时刻、同一答案形状、同一错误通道。业界佐证：XACML PDP/PEP 正典；Spring Security 内部 AuthenticationManager 与 AccessDecisionManager 分立同构；Permit.io 的 PDP 内置限流。

**为什么 deny-by-default**：安全维度的 Noop = 默认放行 = 反模式。

**为什么匿名先验放在判定链上而不是各个 Guard 里**：授权的前提是识别——"未识别的调用方有没有某个权限"这个问法本身不成立。若把这条留给每个 Guard 自行判断，用户自己写的 Guard（例如 `!"alice".equals(subject) ? deny : allow`）就会按 subject 字符串把匿名当普通主体处理，配什么角色就给什么权限。收口在链条上：进任何 Guard 之前先验拒绝匿名，一次覆盖全部 Guard。

**为什么 AccessRequest 要带 Operator 而不只带 subject**：只带字符串时判定层看不见调用方类型，"匿名"只能靠比对字面量 `anonymous` 去猜——猜漏一次就是未认证流量拿到人的权限。带上全貌后类型与租户都对判定层可见（`AccessRequest.inbound(operator, ...)`）；只给 subject 的旧构造保留，此时不替判定层猜。

**被拒绝的备选**：独立权限中间件（拆散管道时刻，多一跳部署）；注解式声明（隐式魔法约定——每个约定必须能指着测试说"违反它会被谁抓住"，见 AGENTS.md）。

## 四、边界

- 不适用：无调用方语义的纯内部批处理
- 网关层防护与应用内判定链是**纵深关系**，不互替
- Guard 顺序语义已定：**首个否决即返回**（fail-fast）；需要全量评估的场景由调用方组合 Guard 自行实现
- 关联：ADR-005；能力答案见 catalog 条目 `access`（权限类 Guards 成套方案，待建）
