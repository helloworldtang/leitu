# 业务数据怎么读写（data-access）

> catalog：capability ｜ required: conditional ｜ status: answered（stability: stable，since 0.2.0） ｜ depends_on: who-is-operating, allow-or-not, what-happened

## 一、为什么

CRUD 是业务开发的主体，三条已答的问题在这里汇合：审计字段要回答"谁建谁改"（who-is-operating）；数据权限是判定链上的一类 Guard（allow-or-not）；读写本身就是"发生了什么"的最大事件源（what-happened）。

没有标准答案时的漂移成本：每个项目自造一套"实体基类 + ORM 拦截器 + 注解逃生口"——审计字段有的盖有的没盖、租户过滤靠人肉记得拼条件、数据权限翻译器各写各的方言；AI 生成每一张表、每一条查询都在猜"这个项目的审计与租户规则是什么"，猜错一次就是一次跨租户泄漏或一笔说不清的改动。

## 二、机制（标准答案的形状）

**数据存取器收口读写：审计四件套由存取器自动盖章、租户是作用域不是实体字段、数据权限走判定链——真库由 adapter 实现，内存实现兜底且大声标注。**

```java
// 已实现：leitu-capability-data 的 cn.youhuale.leitu.capability.data 包
public interface Auditable {                        // 实体自证携带方式（record 一行 wither）
    AuditFields auditFields();
    Auditable withAuditFields(AuditFields auditFields);
}

public record AuditFields(String createdBy, Instant createdAt,   // 审计四件套，成对两态
                          String updatedBy, Instant updatedAt) { /* … */ }

public interface DataStore<T extends Auditable, ID> {            // 存取缝：真库 adapter 实现
    T save(T entity);              // 落库即盖章：插入四件套全新；更新保 created 刷 updated
    Optional<T> findById(ID id);   // 只见当前租户的行；他租户同 id = empty（不泄漏不报错）
    boolean deleteById(ID id);     // 作用域内删除，未见 = false
    List<T> findAll();             // 只列当前租户
}

public final class DataStores {                     // api 工厂
    static <T, ID> DataStore<T, ID> inMemory(Function<T, ID> idOf);              // 兜底：重启即失
    static <T, ID> DataStore<T, ID> inMemory(Function<T, ID> idOf, Clock clock); // 测试可注入时钟
    static <T, ID> DataStore<T, ID> observing(DataStore<T, ID> s, Function<T, ID> idOf,
                                              ObservationRecorder r);            // opt-in 观测装饰
}
```

- **形状**：四操作最小面；实体经 Auditable 声明携带；主键提取器传方法引用（Order::id）
- **兜底**：内存实现（ConcurrentHashMap，键=（租户, 主键）），toString 自我声明「内存实现，重启即失——生产必换 adapter」
- **插拔**：真库 = adapter 实现 DataStore（盖章算法复用 AuditFields.stampedBy / restampedBy）；列表行过滤/分页/查询语言属 adapter
- **成套答案**：数据权限不造新类型——单资源判定=判定链 Guard（resource 携数据主键）；观测=opt-in 装饰器记 data.* 事件
- **收口**：数据权限用 {@code DataStores.guarded(store, chain, "order", Order::id)} 包在存取缝上——
  四种读写各自带判定动作（data:save / data:read / data:delete / data:list），资源名 = 域/主键，
  否决即抛 AccessDeniedException（不降级为"空结果"）；空链 = deny-by-default

## 三、取舍

**为什么审计只有四件套**：谁建/何时建/谁改/何时改是事实，每一行都成立；软删除与乐观锁是策略（要不要删了不见、要不要并发抢跑）——策略由真实项目裁，文档留缝，不预设。

**为什么盖章权在存取器（行存在性定插改）**：实体自带的章不可信（可伪造、可过期）——存取器以行是否已存在为准重落，createdBy/createdAt 永不改变；单一事实源，审计才能当呈堂证供。

**为什么租户是作用域不是实体字段**：租户从执行上下文来（单一事实源）——实体带 tenant 字段就有两个真相，上下文与实体打架时没人说得清行归谁；行归属在写入时定，跨租户同主键是两行。

**为什么"-"不是全局通配**：系统级数据（定时任务落的行）若对所有租户可见，等于系统作用域变成后门；"-" 是一个普通作用域，只对 "-" 上下文可见。

**为什么数据权限走判定链、不做行过滤**：单资源读写的判定由判定链承担（AccessRequest.resource = 数据主键，数据权限就是一类 Guard）；列表行过滤是查询优化，依赖尚未存在的查询语言——翻译器属 adapter。

**为什么判定要收口到存取缝而不是靠自觉**：手写 {@code if (d.isAllow()) store.save(...)} 漏判一次就是一次越权，而漏判编译得过、测试也过得去——"靠自觉"没有失败模式，机器守不住；包了装饰器则每次读写都判，没包就明说没包。否决抛异常而非返回空：把"没权限"伪装成"不存在"，排障时无从分辨。

**为什么内存兜底而不是纯契约**：金样本/开发/测试要开箱即跑（三段交付）；对照观测维度的日志级默认——数据维度给最小可用实现，但必须大声标注边界（toString + 本页）。

**被拒绝的备选**：注解逃生口（隐式魔法）；实体携带 tenant 字段（双源真相）；软删/乐观锁进 v1（策略非事实）；查询条件规则翻译器（依赖查询语言）；JDBC 兜底（capability 零框架依赖，JDBC 绑定属 adapter）；无兜底纯契约（金样本无处跑）。详见 ADR-010。

## 四、边界

- 列表行过滤（查询条件注入）、分页、查询语言：adapter 扩展，v1 只有 findAll
- 事务边界：catalog 条目 transaction-boundary 待裁（验尸中），本答案不预设
- 审计回放（历史轨迹）：本答案给"章"不给"史"——data-changed-by-whom 未答，回放走观测事件流
- 观察装饰器不记异常路径事件——失败形态是 failure-response 的地盘
- 关联：ADR-010；依赖 who-is-operating / allow-or-not / what-happened；金样本 examples/data-access
