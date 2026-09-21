# ADR-015 adapter 层：JDBC 真库路径与 FailureNotice web 投影

状态：已接受（2026-09-21）

## 背景

ADR-014 定下模块布局后，绑定层需要两条「第一条路径」的具体决策：data-access 的「真库由 adapter 实现」（此前只有内存兜底）；failure-response 的「HTTP problem+json 是 adapter 侧投影」（此前只有决策与本体、无投影实现）。两条都在实施方案（阶段三第一批）中列为批次 2–3。

## 决策

**一、leitu-adapter-jdbc（JDBC 真库路径）**

- 绑定方式：spring-jdbc（JdbcClient），零 ORM、零反射、零注解——表/列/行⇄实体映射全部显式给出（JdbcMapping），延续「拒隐式魔法」教义；
- 表约定（v1 固定，扩展留缝）：单表；tenant、id、created_by、created_at、updated_by、updated_at 六列由 adapter 统一读写；业务列由映射声明；
- 三条合同落位：租户作用域（一切语句 WHERE tenant = ?；他租户不可见、不泄漏、不报错；"-" 是系统作用域非通配）；审计盖章（复用 AuditFields.stampedBy / restampedBy，行存在性定插改）；主键 (tenant, id)（跨租户同 id 两行；并发竞争由唯一约束兜底，冲突大声失败）；
- 装配：容器有 DataSource 时提供 JdbcDataStoreFactory（条件装配、用户 Bean 优先）；具体存取器由应用一行声明（泛型实体无法全局兜底，见 ADR-014）；
- 测试：H2 集成金测试（隔离/盖章/键/系统作用域/装饰组合）。

**二、leitu-adapter-web（FailureNotice web 投影）**

- 投影：FailureNotice → application/problem+json（RFC 9457）：type/title/detail/status + 扩展成员 kind/retry/traceId/attributes；
- 状态码：BUSINESS→400、SYSTEM→500、判定否决（guard.denied）→403；可用 notice.attributes() 的 "http.status" 显式覆盖（400–599）；
- 头：X-Trace-Id（关联键）；Retry.Later → Retry-After（秒）；
- 载体异常：FailureNoticeException（本模块）——携带交代、可抛出；判定链否决经 from(Decision, reader) 一行转换；
- 未捕获异常：系统错脱敏交代（fromThrowable，消息与类名不出端）；框架异常（ErrorResponse）保留其状态与说明，仅统一媒体类型；
- 排序与开关：advice 为最低优先级（使用方自己的 advice 优先）；leitu.web.advice-enabled（缺省 true）；
- 框架端点豁免：FrameworkEndpoints 前缀谓词（/v3/api-docs、/swagger-ui、/doc.html、/webjars、/swagger-resources），供统一包装组件豁免；leitu 自身不做无差别包装（ADR-013 增值点 4）。

**三、载体异常归属**：FailureNoticeException 属本 adapter（web 投影的载体）；若未来非 web 传输也需要，迁移至 core 须另立 ADR。

## 后果

- data-access / failure-response / api-contract 三条答案的 adapter 段落地；api-contract 从 drafted 转 answered（五件套补齐，见 catalog）；
- 新增守护规则：Spring 只在 starter 与 adapter；两个 adapter 的 internal 可见性（LeituRules 落地）；
- 测试证据：H2 金测试 9 例 + 投影金测试 4 例 + 豁免谓词 2 例 + 示例应用端到端冒烟（examples/spring-boot）。

## 被拒绝的备选

- MyBatis / JPA 绑定（mapper 世界观摩擦；聚合根语义与「不叫 Repository」边界冲突）；
- 状态码从 type 字符串推断（脆弱）——改为默认映射 + 显式覆盖；
- 无差别 catch-all 包装（验尸教训再犯）——框架异常保留语义；
- WebFlux 投影（v1 只做 Servlet；需要时另立条目）。

## 业界对照

RFC 9457（problem details 标准）；Spring MVC ErrorResponse / ProblemDetail（框架异常语义）；springdoc + knife4j 文档接线（ADR-013）。
