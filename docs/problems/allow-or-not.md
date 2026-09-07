# 允许吗（allow-or-not）

> catalog：pipeline ｜ required: always ｜ status: drafted ｜ depends_on: who-is-operating

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
// 接口草案——阶段二定稿
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
- **成套答案**：`leitu-capability-access`（规划）提供权限类 Guards 与判定链的标准用法；限流/配额为 guard 型能力（先 experimental）

## 三、取舍

**为什么不是窄的权限接口（AuthorizationChecker 式）**：权限只是说"不"的来源之一。统一协议后，限流/配额/熔断共享同一拦截、观测与错误通道——一个问题的答案，而不是四个平行机制。

**为什么限流并入判定链（而非独立问题）**：同一时刻、同一答案形状、同一错误通道。业界佐证：XACML PDP/PEP 正典；Spring Security 内部 AuthenticationManager 与 AccessDecisionManager 分立同构；Permit.io 的 PDP 内置限流。

**为什么 deny-by-default**：安全维度的 Noop = 默认放行 = 反模式。

**被拒绝的备选**：独立权限中间件（拆散管道时刻，多一跳部署）；注解式声明（隐式魔法约定——每个约定必须能指着测试说"违反它会被谁抓住"，见 AGENTS.md）。

## 四、边界

- 不适用：无调用方语义的纯内部批处理
- 网关层防护与应用内判定链是**纵深关系**，不互替
- 待定（实现前决）：Guard 注册顺序语义——首否决即断 vs 全量评估并报
- 关联：ADR-005；能力答案见 catalog 条目 `access`
