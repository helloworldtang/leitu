# 累土 leitu · 文档

> 九层之台，起于累土。——《道德经》

给人和 AI 的确定性后端地基：把实现不同系统时反复出现、边界清楚的问题，沉淀成一套标准答案。

## 两个入口，先分清要去哪

- **要写一个能跑的服务**（接入 leitu）→ [接入清单 INTEGRATION](INTEGRATION.md)：按执行顺序排的逐条清单，别从下面这份「读仓库」的清单开始；
- **要读懂本仓库**（为什么这么设计）→ [README](../README.md) 的「快速开始」，再回到本页的[最小阅读集](#最小阅读集)。

## 导航

- [术语表 GLOSSARY](GLOSSARY.md) —— 本项目一切用词的依据
- [问题目录](../catalog/problems.json) —— 本库回答哪些问题、各自什么状态（机器权威状态）
- [边界声明 SCOPE](SCOPE.md) —— 永不答 / 留缝 / 待答：三类分开，改判需新 ADR
- [接入清单 INTEGRATION](INTEGRATION.md) —— 前置 / 依赖 / 属性 / 建表 / 入口 / 投影 / 自检
- [路线图 ROADMAP](ROADMAP.md) —— 阶段、进度与下一步
- [架构视图](architecture/views.md) —— 由 `scripts/ExportArchitecture.java` 生成的 4R 视图
- [七维体检报告（2026-10-26）](reviews/2026-10-26-七维体检.md) —— 七维评分、扣分点与三张工单

## 已答的问题页（`answered`）

本表由机器核对完整性——新增 `answered` 条目未在此登记，构建会红（`CatalogIntegrityTest`）。

| 问题 | 页面 |
|---|---|
| 这次访问（或出站调用）允许吗？ | [allow-or-not](problems/allow-or-not.md)（[四段式范例](problems/allow-or-not.md)） |
| 谁在操作？代表谁？链路标识？ | [who-is-operating](problems/who-is-operating.md) |
| 发生了什么，怎么记录？ | [what-happened](problems/what-happened.md) |
| 配置值从哪来？ | [config-source](problems/config-source.md) |
| 业务数据怎么读写（审计、租户、数据权限）？ | [data-access](problems/data-access.md) |
| 操作失败了怎么向调用方交代？ | [failure-response](problems/failure-response.md) |
| 怎么缓存（进程内 / Redis）？ | [cache](problems/cache.md) |
| 服务对外暴露了什么？ | [api-contract](problems/api-contract.md) |
| 现成的权限类 Guards 去哪拿？ | [access](problems/access.md) |

## 最小阅读集

1. [术语表 GLOSSARY](GLOSSARY.md)
2. [问题目录](../catalog/problems.json)
3. [问题页范例：允许吗（判定链）](problems/allow-or-not.md)
4. [决策记录 ADR](decisions/README.md) —— 每条设计为什么是现在这样

主仓库：[helloworldtang/leitu](https://github.com/helloworldtang/leitu)
