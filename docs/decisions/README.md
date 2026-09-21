# 决策记录（ADR）

一条决策一篇文章：**为什么**是现在这样、拒绝了什么备选。AI 修改设计前先读这里，防破坏原意图。

| 编号 | 标题 | 状态 |
|---|---|---|
| [ADR-001](ADR-001-terminology-port-spi.md) | 术语体系：Port + SPI，退役"契约"伞词 | 已接受 |
| [ADR-002](ADR-002-core-capability-boundary.md) | core 职责与 core/capability 边界（三测试 + 三段交付） | 已接受 |
| [ADR-003](ADR-003-layered-organize-microkernel-assemble.md) | 架构风格：分层组织，微内核组装 | 已接受 |
| [ADR-004](ADR-004-no-topology-goal-promise-discipline.md) | 删除部署拓扑目标；承诺纪律 | 已接受 |
| [ADR-005](ADR-005-guard-chain.md) | 问题"允许吗"重构为判定链 | 已接受 |
| [ADR-006](ADR-006-answer-admission.md) | 答案入库制度（五件套 + catalog schema） | 已接受 |
| [ADR-007](ADR-007-naming-leitu.md) | 命名与坐标：累土 leitu / cn.youhuale | 已接受 |
| [ADR-008](ADR-008-observation-event.md) | 问题"发生了什么"的答案：观测事件协议 + 日志级默认 | 已接受 |
| [ADR-009](ADR-009-config-source.md) | 问题"配置值从哪来"的答案：三源兜底 + SPI 插拔配置源 | 已接受 |
| [ADR-010](ADR-010-data-access.md) | 问题"业务数据怎么读写"的答案：审计四件套 + 租户作用域 + 数据存取器 | 已接受 |
| [ADR-011](ADR-011-failure-notice.md) | 问题"操作失败了"的答案：FailureNotice 交代结构 + 脱敏默认 | 已接受 |
| [ADR-012](ADR-012-cache.md) | 问题"怎么缓存"的答案：TTL+LRU 进程内缓存 + getOrLoad 装载收编 + 租户作用域键 | 已接受 |
| [ADR-013](ADR-013-api-doc-integration.md) | 问题"服务对外暴露了什么"的答案：不自研，集成 springdoc + knife4j | 已接受 |
| [ADR-014](ADR-014-starter-assembly-layer.md) | starter 装配层：模块布局、宿主版本管理与 BOM | 已接受 |
| [ADR-015](ADR-015-adapter-jdbc-web.md) | adapter 层：JDBC 真库路径与 FailureNotice web 投影 | 已接受 |

## 制度

- ADR 记录**已接受**的决策；被新决策替代时标"被替代"并指向新篇，不删除。
- 每条 ADR 必含：背景（问题与动机）、决策、后果（含被拒绝的备选及理由）。
- 修改 core 设计、边界规则、术语，必须先有 ADR。
