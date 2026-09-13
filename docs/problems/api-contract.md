# 服务对外暴露了什么（api-contract）

> catalog：pipeline ｜ required: conditional ｜ status: drafted ｜ depends_on: —

## 一、为什么

API 文档是业务开发链的最后一环，也是漂移最显眼的一环：实现改了文档没改、自研目录各自维护、示例与正文自相矛盾——调用方（人、AI、网关）拿不到可信的"这个服务暴露了什么"。

没有标准答案时的漂移成本：每个项目自造目录或干脆裸奔——接口变更靠口口相传；AI 生成客户端代码时猜参数猜响应；联调与工单排查全部退化为读源码。

## 二、机制（标准答案的形状——已定策，实现随 starter 层落地）

**不自研：API 文档走 springdoc + knife4j（OpenAPI 3 事实标准）；声明用注解+扫描，人读面用 knife4j UI。**

```text
// 待实现：leitu starter 层接线（adapter/starter 层开启后，另行规划）
// 1) FailureNotice → problem+json 的 schema 对齐（ADR-011 投影落地）
// 2) traceId 响应头提示（失败交代关联键进文档示例）
// 3) 判定链 action 与 operationId 点分同源对齐
// 4) 文档端点豁免统一包装的框架级约束
```

- **形状**：OpenAPI 3 标准描述 + springdoc 注解声明 + knife4j UI 呈现——全部消费业界标准，本库零自有约定
- **兜底**：引 starter 依赖即得开箱文档（待建——本库今天无 Spring 模块，starter 层开启是前置）
- **成套答案**：leitu 的增值在接线四点（schema 对齐 / traceId / 动作同源 / 端点豁免），见 ADR-013

## 三、取舍

**为什么不造轮子**：OpenAPI 已赢——标准描述、生成器、UI、客户端生态全在标准侧；自研目录 = 双份维护 + 永远追不上的漂移。本库的确定性哲学用在"选哪条标准答案"上（一处决策坍缩），不用在"再造一套"上。

**为什么注解+扫描与本库教义不冲突**：教义（三次拒注解式）拒绝的是本库**自造**的隐式魔法——每个约定要能指着本库测试说"违反会被谁抓住"。消费生态标准约定不违：springdoc 注解是 OpenAPI 标准的 Java 表达，约定由生态守护，本库不新增自有魔法（同用 JDK 注解不算魔法）。

**为什么 leitu 仍有增值**：标准件不知道 FailureNotice 的 problem+json 投影、不知道 traceId 关联键、不知道判定链动作命名——接线四点是本库答案与标准件的粘合处，也是唯一该写的代码。

**被拒绝的备选**：自研操作目录（造轮子）；自研 Markdown 渲染（knife4j UI 已是人读面）；错误码注册表（type 点分标识已定）；在 core 定义 OpenAPI 子集（传输投影不进 core）。详见 ADR-013。

## 四、边界

- 本页是决策不是实现：catalog 状态 drafted = 成文未实现（诚实态）；实现 = 第一个 starter 模块，随 adapter/starter 层开启落地
- starter 层开启是整套架构决策（宿主版本管理 / BOM / 模块布局），另行专门规划——不因本条赶工
- 关联：ADR-013；对接的既有答案 failure-response（problem+json 投影）、who-is-operating（traceId）、allow-or-not（action 同源）
