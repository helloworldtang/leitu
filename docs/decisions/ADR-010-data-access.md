# ADR-010 问题"业务数据怎么读写"的答案：审计四件套 + 租户作用域 + 数据存取器

状态：已接受（2026-09-13）

## 背景

CRUD 是业务开发的主体；三条已答问题在此汇合——审计字段要回答"谁建谁改"（who-is-operating）、数据权限是判定链上的一类 Guard（allow-or-not）、读写本身就是"发生了什么"的最大事件源（what-happened）。对常见持久层实践验尸得三条结论（独立论证）：

1. **审计靠 ORM 拦截器/实体注解隐式填充**——填充时机与来源不可见，业务以为盖了章实际没盖；注解式逃生口（局部关闭）更是隐式魔法约定（本库已在 allow-or-not 否决注解式声明）；
2. **审计者/租户的取值 provider 被推到各接入方实现**——凡把"内核"与"数据能力"分层摆放的架构，默认实现就没处放，每个 adapter 重写一遍读上下文的胶水。累土的能力模块依赖 core，上下文直读的默认实现可以内置于能力盒内；
3. **数据权限在框架层翻译成查询条件**——依赖具体查询语言，翻译器会长成第二个查询生成器。

## 决策

标准答案 = **DataStore 存取缝 + 盒内盖章/作用域算法 + 内存兜底**：

- **审计四件套** createdBy/createdAt/updatedBy/updatedAt 进 v1，成对两态（全空/全满）构造期强制；软删除（deletedBy/at）与乐观锁（version）是策略不是事实，留缝不入 v1
- **盖章权在存取器**：插入/更新以行是否已存在为准（当前租户作用域内），createdBy/createdAt 永不改变；实体自带的章不作数（单一事实源）。实体经 `Auditable` 自证携带方式（读 + wither 盖章拷贝，record 一行协变实现），时机与内容不由实体决定
- **租户是作用域不是实体字段**：从执行上下文来，行归属写入时定；主键在租户作用域内唯一，跨租户同 id 是两行；"-" 是系统级作用域不是全局通配
- **数据权限=判定链 Guard 侧**（AccessRequest.resource 携数据主键）；列表行过滤（查询条件注入）是查询优化，属 adapter——不造规则翻译器
- **观察装饰器 opt-in**（`DataStores.observing(...)`）记 data.* 事件；默认不记，装配处组合可见；异常路径不记事件（失败形态是 failure-response 的地盘）
- **内存兜底大声标注**（toString 自我声明「重启即失，生产必换 adapter」），金样本/开发/测试开箱即用——对照观测维度的日志级默认；真库由 adapter 实现 DataStore（盖章算法复用 AuditFields.stampedBy/restampedBy）
- **DataStore 最小面**：save / findById / deleteById / findAll；过滤/分页/查询语言属 adapter

## 业界对照

Spring Data auditing（@CreatedBy/@LastModifiedBy + Auditable 接口——审计接口下放实体、基础设施填充的同构；差异：盖章算法在盒内一份而非容器回调）；MyBatis-Plus 自动填充与租户插件（列模式租户的查询条件对照）；JPA @Version 与 @Where 逻辑删除（策略类能力的对照=留缝的理由）；多租户三模式（独立库/共享库独立 schema/共享表租户列——本答案是共享表租户列之上的读写作用域层，隔离强度由 adapter 的库表结构决定）。不叫 Repository：DDD Repository 携带聚合根语义与派生查询预期，恰是本答案不承诺的东西（术语三关第三关）。

## 后果

- adapter 获得明确接缝：实现 DataStore + 复用盖章算法，不再各写胶水（修正验尸结论 2）
- rules 资产扩展：LeituRules 的能力模块 internal 规则按既定缝参数化出第二条常量（INTERNAL_只被本能力模块访问_数据），access 常量语义不变
- 审计成对不变量对历史半章数据偏严——adapter 映射层负责归一，合同面向新答案
- 内存实现有一处已注释的协变强转（Java 无自类型的代价，运行期由协变返回保证）；并发 save 同主键 last-write-wins——这正是乐观锁留缝要解决的问题

**被拒绝的备选**：注解逃生口（隐式魔法约定）；实体携带 tenant 字段（双源真相，上下文与实体可打架）；软删除/乐观锁进 v1（策略非事实，等真实项目疼了再裁）；查询条件规则翻译器（依赖查询语言，翻译器=第二个查询生成器）；JDBC 兜底（capability 零框架依赖，JDBC 绑定属 adapter）；无兜底纯契约（违背三段交付，金样本无处跑）；`Repository` 命名（承诺派生查询与聚合根语义）；CRTP 自类型 Auditable（可用性代价）；always-on 观测（第一处隐藏自动记录，与全库显式装配相悖）。
