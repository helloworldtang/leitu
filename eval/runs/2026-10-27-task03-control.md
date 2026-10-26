# 任务 03-对照（no-leitu）采样报告

- 日期：2026-10-27；实现者：独立 agent（对照组，仅 Spring 官方 starter）
- 工程：/tmp/leitu-control-03/work/fx-service（Spring Boot 3.5.4，Java 21，Maven 3.9.9）
- 完整 mvn 日志：/tmp/leitu-control-03/mvn-first-test.log（首次）、mvn-second-test.log（修复后）、mvn-package.log

## 一、四个数

| 项 | 读数 |
|---|---|
| 完成率 | 6/6（逐条自证见下） |
| 一次绿灯 | **否**。首次 mvn test 编译失败（FxRequestLifecycleFilter.java:72 `outcome 可能尚未初始化`），修复一处后第二次 7/7 全绿 |
| 时长（开始 → 首次 mvn test 结束） | **51 秒**（date +%s：1793054504 → 1793054555；起点=读完任务卡、任何实现开始前） |
| 歧义点 | 10 条（见第四节） |

注：起点取在读完任务卡之后；任务卡阅读未计入时长（两侧口径一致按此理解执行）。

## 二、验收 6 条自评（全部可自证）

| # | 条款 | 自评 | 证据（复跑命令） |
|---|---|---|---|
| 1 | mvn test 全绿且 3~6 各有断言 | ✅（最终态） | `cd /tmp/leitu-control-03/work/fx-service && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home /tmp/leitu-control-02/tools/apache-maven-3.9.9/bin/mvn -B -ntp test` → Tests run: 7, Failures: 0, Errors: 0；3~6 条对应测试见下各行 |
| 2 | 换算配置驱动 | ✅ | RateConfigVariantATests（usd-jpy=155.5 → rate 155.5 / result 15550.0）与 RateConfigVariantBTests（usd-jpy=150 → 150.0 / 15000.0）：同一请求仅改配置值，断言结果变化 |
| 3 | 每次调用一条结构化事件（事件名/操作者/traceId/耗时） | ✅ | ConvertApiTests.everyCallProducesEventWithNameOperatorTraceIdAndDuration：注入 InMemoryFxEventPublisher，清空→发一次请求→断言恰好 1 条事件且 name="fx.convert"、operator="alice"、traceId 固定值、durationMs≥0 |
| 4 | 未知币对非 200 非 500 机器可读 JSON；非授权 403 | ✅ | ErrorGuardTests.unknownCurrencyPair…（404 + code=UNKNOWN_CURRENCY_PAIR + traceId）；unauthorizedSubjectGets403（403 + code=UNAUTHORIZED_SUBJECT + traceId）。curl 实录见第五节 |
| 5 | 响应头 traceId 与事件 traceId 一致 | ✅ | ConvertApiTests.responseHeaderTraceIdMatchesEventTraceId（自动生成场景）+ everyCall…（固定透传场景）+ ErrorGuardTests 中 403 路径断言事件 traceId==响应头；curl 手工核对头与体同值 |
| 6 | 登记四项 | ✅ | 即本报告 |

补测：ErrorGuardTests 额外断言 403 也产生事件（outcome=UNAUTHORIZED）；ApiExceptionHandler 另给缺参/类型错 400 INVALID_REQUEST（验收未要求，见歧义 7）。

## 三、pom 依赖（parent spring-boot-starter-parent 3.5.4）

- org.springframework.boot:spring-boot-starter-web（compile）——内含 starter/core/json/tomcat/web/webmvc
- org.springframework.boot:spring-boot-starter-test（test）——JUnit 5、AssertJ、spring-test(MockMvc)、json-path 等
- 无其他第三方依赖；插件仅 spring-boot-maven-plugin

## 四、歧义点清单（位置 | 猜了什么 | 依据）

1. 响应体结构（目标段只给 URL） | {from,to,amount,rate,result,traceId} | 卡片未定响应结构，traceId 放体内便于核对
2. 汇率配置形状（「汇率从外部配置来」） | `fx.rates.usd-cny: 7.2` 小写连字符键 + BigDecimal | 卡片未定；避開 Spring map 键对大写/`>`等字符的转义陷阱
3. 「换算」语义 | 只支持直连币对，不做中间货币交叉推导 | 卡片未提交叉汇率，且未知币对错误需要可触发
4. 操作者身份载体与授权来源 | 请求头 X-Operator + 配置 fx.authorized-subjects，缺失头记 anonymous | 卡片要求事件含操作者、非授权 403，未定身份怎么带、谁说了算
5. traceId 生成与头名 | 头名 X-Trace-Id；优先透传请求头，否则 UUID | 卡片只要求「响应头与事件一致」；透传使测试可确定断言
6. 金额/精度 | BigDecimal，结果 scale=2 HALF_UP | 货币惯例；卡片未提精度
7. 未知币对之外的坏输入 | 缺参/类型错也回 400 INVALID_REQUEST JSON（含 traceId），非验收要求 | 卡片只点名两类失败；为错误路径一致性补齐
8. 403 是否也发事件 | 发，outcome=UNAUTHORIZED | 「每次调用产生一条结构化事件」按字面执行
9. Spring Boot 版本 | 3.5.4 | 卡片未定；Central 已核实存在（metadata 拉取），JDK21 兼容稳定线
10. 币种代码大小写 | from/to 归一化为大写后查找 | ISO-4217 惯例；卡片未说

（另记非歧义：首次红灯是我自身代码的确定赋值编译错，非任务卡问题。）

## 五、启动后请求与响应（java -jar target/fx-service-0.0.1-SNAPSHOT.jar --server.port=18080）

```
== GET /fx/convert?from=USD&to=CNY&amount=100  (X-Operator: alice) ==
HTTP/1.1 200
X-Trace-Id: 34c6ba15-8de7-4974-b80e-fa5b43ef75f1
{"from":"USD","to":"CNY","amount":100,"rate":7.2,"result":720.00,"traceId":"34c6ba15-8de7-4974-b80e-fa5b43ef75f1"}

== GET /fx/convert?from=USD&to=GBP&amount=50  (X-Operator: alice) ==
HTTP/1.1 404
X-Trace-Id: a1766e5c-370d-43ba-8997-bf5f6b53f118
{"code":"UNKNOWN_CURRENCY_PAIR","message":"no configured exchange rate for currency pair: USD->GBP","traceId":"a1766e5c-370d-43ba-8997-bf5f6b53f118"}

== GET /fx/convert?from=USD&to=CNY&amount=100  (X-Operator: mallory) ==
HTTP/1.1 403
X-Trace-Id: 1ef899e9-77dc-482b-9362-d8d87a5437ef
{"code":"UNAUTHORIZED_SUBJECT","message":"operator 'mallory' is not authorized","traceId":"1ef899e9-77dc-482b-9362-d8d87a5437ef"}
```

## 六、验证分层说明

- 运行并观察：首次/第二次 mvn test（7 用例）、mvn package、真机启动 + 三类 curl。
- 检视（未单独运行验证）：WebMvc 对 Filter 的注册顺序、真实并发调用下事件顺序（CopyOnWriteArrayList 写入非全局有序但单请求单事件，风险低）。
- 未验证：生产部署形态（外部化配置热更新后的行为）、性能。
