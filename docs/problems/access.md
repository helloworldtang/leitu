# 访问判定的成套答案（access）

> [允许吗（判定链）](allow-or-not.md)回答的是"判定怎么运转"；本页回答"**现成的权限类 Guards 去哪拿**"。
> 判定链是机制，本页是开箱件——目录条目 `access`，金样本 `examples/access`，0.1.0 交付。

## 一、为什么

判定链给的是"怎么判"（声明即收编、deny-by-default、任一可否决），但每个项目仍然要自己回答"判什么"：

- **角色判断**——`if ("admin".equals(user))` 散落在各个端点里，每个项目一套写法，改权限要全库翻；
- **限流 / 配额**——"这个调用方这分钟还能调几次"，手写计数器，窗口语义十有八九写错。

这类 Guards 写一次就该处处一样：它们是横切协议的"认领动作"的现成件，不是业务逻辑。
所以与判定链同批交付一套参考实现：拿到即用，不满意沿缝换掉，业务代码零改动。

## 二、机制（标准答案的形状——已交付：capability `access`）

三个角色各就各位：**扩展缝**（换模型不改业务）、**默认实现**（声明式 RBAC-lite）、**预算 Guard**（窗口限次）。

```java
// ① 声明式权限映射：主体→角色、动作→所需角色。"谁能做什么"一张表即全部真相。
RoleMap map = RoleMap.create()
        .subject("alice").hasRoles("admin")
        .subject("bob").hasRoles("member")
        .action("order:cancel").allowsRoles("admin")
        .action("order:view").allowsRoles("admin", "member")
        .build();

// ② 从工厂取 Guard，声明即收编进判定链（与手写 Guard 完全同位）
@Bean
Guard access() {
    return AccessGuards.roleMap(map);
}

// ③ 预算 Guard：窗口内限次，耗尽后否决并携带重试语义（refillAfter）
@Bean
Guard aiCalls() {
    return AccessGuards.budget("ai-calls", BudgetSpec.of(100, Duration.ofMinutes(1)));
}
```

要点：

- **`PermissionPolicy` 是扩展缝**：`AccessGuards.roleMap(...)` 返回的就是它——想把 RBAC-lite 换成
  ABAC / 外部权限服务，实现同一个接口即可，判定链与业务代码都不用动；
- **`RoleMap` 是纯数据**：构建后不可变、进程内查表 O(1)；**未配置的主体 / 动作一律不放行**
  （deny-by-default 在 Guard 侧执行，表只陈述事实）；
- **`BudgetSpec` 语义**：简单计数窗口——窗口内允许 `limit` 次，耗尽后否决并告知 `refillAfter` 后可重试；
  测试注入 `Clock` 拨时钟即可断言窗口重置，不必真等。

依据：[allow-or-not](allow-or-not.md)（判定链本体）；可运行对照 `examples/access`。

## 三、取舍

**为什么是 RBAC-lite 而不是完整 RBAC / ABAC**：多数服务的权限真相就是"谁有什么角色、动作要什么角色"，
一张表查完；继承、属性条件、动态策略管理界面都是真实复杂度，但对多数服务是过度设计——需要时沿
`PermissionPolicy` 缝接入，不进默认实现。缝在，答案就不封死。

**为什么预算用简单计数窗口**：够薄、够用、语义可预期（到点重置，没有突发桶的意外）；令牌桶 / 滑动窗口
是策略变体，走扩展缝。承诺纪律：默认实现只承诺能被机器守护的事（计数与窗口可测试）。

**为什么"未匹配 = 拒绝"放在 Guard 侧而不是表里**：`RoleMap` 只陈述事实（谁有什么角色），
"没提到就拒"是判定协议的一部分——数据与策略分离，换权限模型时协议不变。

## 四、边界

- **不做认证**：主体从执行上下文来（[who-is-operating](who-is-operating.md)）；本能力只回答"这个主体允许吗"；
- **不承诺**：角色继承、属性条件（ABAC）、动态权限管理界面、分布式限流——需要时实现 `PermissionPolicy`
  或另立 Guard，本答案留缝不预留；
- **预算计数在进程内**：重启即清零，多实例不共享——跨进程互斥与共享计数是 `lock` 条目的事（尚未交付）；
- **未匹配 = 拒绝**：想"默认放行"得自己写 Guard，但不建议——那是把白名单倒过来，泄漏只差一次手滑。
