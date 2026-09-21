# ADR-014 starter 装配层：模块布局、宿主版本管理与 BOM

状态：已接受（2026-09-21）

## 背景

阶段三（成套交付与实证）开启的第一批：8 条 answered 的第三段交付（「引依赖即用」的默认装配）尚无载体——本库此前没有 Spring 模块。ADR-013 已把 API 文档的实现绑定在 starter 接线（四条增值点）；路线图将「starter 层规划」列为主线和 0.2.0 发版前置，并点名这是整套架构决策（宿主版本管理 / BOM / 模块布局），须专文定夺，不因单条答案赶工。

## 决策

**一、模块布局**——新增三个模块，接入既有单向分层（starter → adapter → capability → core）：

| 模块 | 层 | 职责 |
|---|---|---|
| leitu-spring-boot-starter | 装配面 | 全部端口的默认装配（用户 Bean 优先）+ API 文档接线 |
| leitu-adapter-jdbc | 绑定面 | DataStore 的 JDBC 真库路径（显式映射，零反射零注解） |
| leitu-adapter-web | 绑定面 | FailureNotice 的 HTTP 投影（problem+json）+ 框架端点豁免 |

**二、宿主版本管理**：跟随 Spring Boot 当前稳定次要线——本批锁定 **4.1.x（4.1.1）**。3.5 线 OSS 支持已于 2026 年年中结束，不再作为新代码基线。升级=显式决策动作：版本单点声明于 parent，跟随新次要线须改版本属性并留下记录。

**三、BOM**：leitu-parent 以 import scope 引入 spring-boot-dependencies；springdoc / knife4j 版本由 parent 统一管理；本轮不单独发布 leitu-bom（规模未到，记为演进选项）。

**四、条件装配策略**：

- 全量 @ConditionalOnMissingBean（用户 Bean 优先）；条件可见（@ConditionalOnClass / @ConditionalOnProperty / @ConditionalOnBean）；分维度默认值延续（观测=日志级、缓存=可 Noop、授权=deny-by-default）。
- 实施期两处「自动生成」的收敛（对实施方案草案的修订，均向「显式优先」教义让步）：
  1. **数据存取器**：实体泛型（DataStore&lt;T, ID&gt;）决定无法做全局兜底自动装配——starter 提供 JdbcDataStoreFactory Bean + 使用方一行声明；内存实现维持 API 级兜底定位（测试/演示），不进自动装配。
  2. **权限/预算 Guard**：不做配置式自动生成（避免新造一套配置魔法）——应用声明 Guard Bean，starter 自动收编进判定链；access 能力维持「PermissionPolicy 扩展缝」定位。

**五、规则扩展**（先本 ADR、后 LeituRules 落地）：① Spring 依赖只允许出现在 starter 与 adapter（core / capability 补零框架守卫）；② 两个新 adapter 的 internal 可见性规则（同款）。

## 后果

- 发布集合 6 → 9 个模块；examples 新增 spring-boot 金样本（不发布）；
- starter 为「套餐式」单入口（含两个 adapter，条件激活）——非 web / 非 JDBC 场景依赖面略宽，接受为 v1 取舍；演进：需要时按能力拆分 starter（记为未来决策）；
- 版本升级纪律：宿主次要线每次跟随均需显式决策（改 parent 版本属性 + 记录）。

## 被拒绝的备选

- **双模块 starter（starter + autoconfigure 分离）**：Spring 官方惯例，为本库 v1 规模增加模块税而收益极小；
- **按能力拆多个 starter**：过度设计，现阶段无多入口需求；
- **Boot 3.5.x 线**：OSS 支持已结束；生态（knife4j Next / springdoc 3）已完成向 4.x 的代际迁移；
- **配置式 RBAC / 数据装配全局兜底**：新造魔法或不可实现（泛型），见决策四的两处收敛。

## 业界对照

Spring Boot 第三方 starter 惯例（依赖聚合 + 自动装配 + 条件可见）；Knife4j Next（5.x 线）与 springdoc-openapi 3.x 为 Spring Boot 4 代际的中文生态标准组合（检索 2026-09-21）。
