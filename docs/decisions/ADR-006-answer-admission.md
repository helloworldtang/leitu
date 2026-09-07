# ADR-006 答案入库制度

状态：已接受（2026-09-07）

## 决策

**答案五件套**：问题页（`docs/problems/`）+ 代码 + 守护规则 + 金样本（`examples/`）+ 目录条目（`catalog/`）。CI 强制齐全——**缺一件不是答案，是半成品**。

**问题准入流程**（一个问题的进库之路）：

```
三法推导（场景枚举 / 真实痛点 / 漂移成本）
  → 三准入测试（管道 / 必然 / 形状，见 ADR-002）
  → 定档（无条件必答 / 条件必答 / open 候选）
  → 三段交付（形状 + 安全兜底 + 成套答案，见 ADR-002）
  → 五件套齐 → status: answered
```

**catalog schema**：

| 字段 | 说明 |
|---|---|
| id / problem | 唯一标识 / 问题陈述 |
| facet | pipeline \| capability |
| required | always \| conditional |
| status | open → drafted → answered → deprecated → removed |
| stability | frozen（core 端口）\| stable \| experimental（孵化/食谱态） |
| since / replaced_by | 入库版本 / 退役时指向替代答案 |
| depends_on | 答案依赖图：AI 学习路径 + 答案级爆炸半径 |
| links | 五件套之四的指针（条目本身是第五件），CI 校验存在性 |

设计要点：
- status 含 **drafted**：设计已成文、实现未落——文档先行阶段的诚实态
- **单一实体类型，不造类型动物园**：孵化/食谱用 stability=experimental 表达，不新增类型
- **退役三步**：deprecated 标记 → 迁移指南 → 大版本移除。答案库要能换血，不只增厚

## 后果

问题清单不是任何人钦定的，是有准入制度的活清单——新问题（秘密独立/特性开关/入参验证/对外契约）以 open 状态等待真实痛点升级。
