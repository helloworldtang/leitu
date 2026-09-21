# 服务对外暴露了什么（api-contract）

> catalog：pipeline ｜ required: conditional ｜ status: answered（stability: stable，since 0.2.0） ｜ depends_on: —

## 一、为什么

API 文档是业务开发链的最后一环，也是漂移最显眼的一环：实现改了文档没改、自研目录各自维护、示例与正文自相矛盾——调用方（人、AI、网关）拿不到可信的"这个服务暴露了什么"。

没有标准答案时的漂移成本：每个项目自造目录或干脆裸奔——接口变更靠口口相传；AI 生成客户端代码时猜参数猜响应；联调与工单排查全部退化为读源码。

## 二、机制（标准答案的形状——已交付：starter 接线 + adapter-web 投影）

**不自研：API 文档走 springdoc + knife4j（OpenAPI 3 事实标准）；声明用注解+扫描，人读面用 knife4j UI。**

```text
已实现（leitu-spring-boot-starter + leitu-adapter-web，ADR-014 / ADR-015）：
1) FailureNotice → problem+json 投影（application/problem+json，RFC 9457；判定否决 403 / 系统错脱敏）
2) traceId 响应头（X-Trace-Id）由投影携带——失败交代的关联键，进文档示例
3) 判定链 action 与 operationId 点分同源：命名建议 + 金样本示范（examples/spring-boot）
4) 框架端点豁免谓词 FrameworkEndpoints（/v3/api-docs、/doc.html、/webjars…）供统一包装组件豁免
```

- **形状**：OpenAPI 3 标准描述 + springdoc 注解声明 + knife4j UI 呈现——全部消费业界标准，本库零自有约定
- **兜底**：引 starter 依赖即得开箱文档（已交付：springdoc + knife4j 随 starter 装配，条件激活）
- **成套答案**：leitu 的增值在接线四点（schema 对齐 / traceId / 动作同源 / 端点豁免），见 ADR-013

## 三、取舍

**为什么不造轮子**：OpenAPI 已赢——标准描述、生成器、UI、客户端生态全在标准侧；自研目录 = 双份维护 + 永远追不上的漂移。本库的确定性哲学用在"选哪条标准答案"上（一处决策坍缩），不用在"再造一套"上。

**为什么注解+扫描与本库教义不冲突**：教义（三次拒注解式）拒绝的是本库**自造**的隐式魔法——每个约定要能指着本库测试说"违反会被谁抓住"。消费生态标准约定不违：springdoc 注解是 OpenAPI 标准的 Java 表达，约定由生态守护，本库不新增自有魔法（同用 JDK 注解不算魔法）。

**为什么 leitu 仍有增值**：标准件不知道 FailureNotice 的 problem+json 投影、不知道 traceId 关联键、不知道判定链动作命名——接线四点是本库答案与标准件的粘合处，也是唯一该写的代码。

**被拒绝的备选**：自研操作目录（造轮子）；自研 Markdown 渲染（knife4j UI 已是人读面）；错误码注册表（type 点分标识已定）；在 core 定义 OpenAPI 子集（传输投影不进 core）。详见 ADR-013。

## 四、边界

- 已落地：catalog 状态 answered；实现 = leitu-spring-boot-starter（springdoc + knife4j 接线）+ leitu-adapter-web（投影四点）；宿主与版本策略见 ADR-014
- 接线只承诺四条增值点（schema 对齐 / traceId / 动作同源 / 端点豁免）——超出部分不承诺（承诺纪律）
- 关联：ADR-013 / ADR-014 / ADR-015；对接的既有答案 failure-response（problem+json 投影）、who-is-operating（traceId）、allow-or-not（action 同源）
