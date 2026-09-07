# 谁在操作（who-is-operating）

> catalog：pipeline ｜ required: always ｜ status: answered（stability: frozen，since 0.1.0）

## 一、为什么

几乎每一层都要回答"这次是谁在干活"：判定链要判"这个人/服务/代理允不允许"，观测要记"谁干的"，审计要落"created_by"，数据能力要按租户隔离，链路要用 traceId 串起来。没有标准答案时的漂移成本：每个项目自造 ThreadLocal、自造 header 透传、自造"当前用户"工具类——AI 生成时每一处都要猜"这个项目从哪取用户"，猜法五花八门。

## 二、机制（标准答案的形状）

**身份、租户、链路标识束成一组，一次绑定、处处只读。**

```java
// 已实现：leitu-core 的 cn.youhuale.leitu.core.context 包
ExecutionContextReader who = ExecutionContextReader.threadLocal();  // 注入使用

who.current().operator().subject();   // 谁
who.current().operator().tenant();    // 代表谁（无租户语义 "-"）
who.current().operator().kind();      // HUMAN / SYSTEM / AGENT——AI 代理是一等操作者
who.current().traceId();              // 这次调用链的锚点
```

- **绑定是宿主/入口的事**（`ExecutionContextBinder` SPI，try-with-resources 作用域，退出自动还原不泄漏）；**业务只读**（`ExecutionContextReader` 端口，最小权限）
- **兜底**：未绑定读到匿名上下文（`subject=anonymous` + 自动生成的 traceId），永不抛异常——匿名者过判定链会被权限类 Guard 拦下，纵深成立
- **默认实现**：线程级绑定（`threadLocal()`）；测试可注入任意 fake binder
- **成套答案**：身份源对接、操作者富模型（`leitu-capability-identity`，待建）

## 三、取舍

**为什么身份+租户+traceId 束在一起**：它们在同一次绑定时同时确定、在同一次传播中同行、被同一批消费方读取——变化原因相同，拆开则每个入口都要绑三次。

**为什么 kind（HUMAN/SYSTEM/AGENT）进 core**：AI 代理与真人同场操作是这个时代的既成事实；判定（agent 权限策略不同）和观测（区分人/代理行为）都需要它。现在加是三行代码，将来加是全量数据迁移。

**为什么兜底是"匿名"而不是抛异常**：上下文缺席不是错误状态（健康检查、启动任务没有"操作者"），抛异常会把管道其他答案拖垮；而匿名身份在判定链处自然被拦，安全不裸奔。

**被拒绝的备选**：纯参数透传（侵入所有签名）/ 静态全局 holder 无作用域（泄漏与还原失控）/ 只给 ThreadLocal 不给 SPI（宿主无法换绑定点，如虚拟线程/消息驱动场景）。

## 四、边界

- 跨线程/异步传播是 binder 实现的责任（core 提供值对象与作用域协议，不提供魔法）
- 租户隔离的执行（SQL 改写等）属数据能力，这里只提供租户标识
- 关联：allow-or-not 依赖本答案的 `subject`；能力答案见 catalog 条目 `identity`（待建）
