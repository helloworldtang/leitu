# 任务 02-对照（不用累土）采样记录 —— 带缓存的 Spring Boot 目录服务

- 日期：2026-10-26｜执行者：独立开发者（AI，对照组采样）
- 工作目录：/tmp/leitu-control-02/work/book-catalog（从零创建）
- 本文件是 /tmp/leitu-control-02/report.md 的 workspace 交付副本（内容一致）
- 对照约束：只用 Spring 官方 starter 与官方文档；未引入 leitu / cn.youhuale / 任何非 Spring 官方框架或工具库；未读 leitu 仓库除任务卡外任何文件。

## 环境
- JDK：Temurin 21.0.10（JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home）
- Maven：自备 apache-maven-3.9.9（Maven 不在 PATH；dlcdn 两个版本 404、archive 镜像 300s 超时，最终从 Maven Central 发行包 org/apache/maven/apache-maven 取得——这段弯路计入时长）
- Spring Boot：3.5.14（spring-boot-starter-parent 3.5.14；~/.m2 已缓存该版本，Central 可达）

## 四个数
1. **完成率：6/6**（逐条自评见下节，全部有命令级自证）
2. **一次绿灯：是**。首次 `mvn test`：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`，BUILD SUCCESS，退出码 0（5 个测试，非零测试的绿）
3. **时长：555 秒（约 9 分 15 秒）**。任务开始 `date +%s`=1793021071 → 首次 `mvn test` 结束=1793021626（口径与任务 01 一致）。其中首次 mvn test 本身 8 秒；耗时大头是 Maven 发行包下载弯路（dlcdn 404 ×2 + archive 超时 300s，后改走 Central）
4. **歧义点：11 条**（完整清单见下）

## 验收逐条自评（6 条，全过）

| # | 条目 | 结论 | 自证（命令与观察） |
|---|------|------|--------------------|
| 1 | mvn test 全绿且 2~6 各有断言 | 通过 | 首次 `mvn -B test` → Tests run: 5, Failures 0, Errors 0（surefire：target/surefire-reports）。映射：验收2→createThenReadBackShowsCreator；验收3→unauthorizedCreateIs403WithMachineReadableBody；验收4→twoReadsOfSameIdTouchStorageExactlyOnce + storageStatsEndpointExposesTouchCount；验收5→tenantBReadsTenantADataAs404AndNeverSeesACacheCopy；验收6→本报告（登记动作本身，见歧义 #8） |
| 2 | 审计：读回可见创建者 | 通过 | 测试断言 `$.createdBy == "alice"`；真机：POST(alice)→201 响应与 GET 响应均含 `"createdBy":"alice"`（转录 §2/§4） |
| 3 | 非授权创建→403 + 机器可读 JSON（错误标识+traceId） | 通过 | 测试断言 403 + `$.error=="ACCESS_DENIED"` + `$.traceId` 非空 + `$.status==403`；真机 mallory→403 body：`{"error":"ACCESS_DENIED","message":"principal mallory is not allowed to create books","traceId":"01b328e0-...","status":403,...}`（转录 §3） |
| 4 | 同 id 读两次，底层只触达一次（计数探针） | 通过 | 探针：BookRepository.find 入口 AtomicLong 自增 + GET /internal/storage-stats 可读。测试：delta==1；真机：读前 0 → 首读后 1 → 再读后仍 1（转录 §5/§7） |
| 5 | 租户隔离：B 读 A 的 id → 非 200 非 500，不得读 A 缓存副本 | 通过 | 真机：globex 读 acme 的 id → 404 BOOK_NOT_FOUND，body 不含 Dune/Frank Herbert/acme；touches +1（B 是缓存 miss、走的是存储而非 A 的缓存项）；随后 acme 再读 → 200 且 touches 不变（A 的缓存项未被 B 污染，键=tenant:id，转录 §8/§9）；测试 tenantBReadsTenantADataAs404AndNeverSeesACacheCopy 同路径断言 |
| 6 | 登记四个数 | 通过 | 即本报告：完成率 6/6、一次绿灯=是、时长 555s、歧义 11 条 |

## 完整歧义清单（位置 | 我猜了什么 | 依据）

| # | 位置 | 我猜了什么 | 依据 |
|---|------|-----------|------|
| 1 | 数据存取选型 | 进程内 ConcurrentHashMap 存储 + AtomicLong 计数探针，不引 H2/JDBC | 卡明说「关系库或进程内存储皆可」，对照组取最小依赖最快路径 |
| 2 | 主体/租户识别方式 | 请求头 X-Tenant-Id 与 X-User | 卡未定义认证与租户机制，需要可判定的主体来源 |
| 3 | 授权规则 | 固定写者白名单 alice/bob 可创建，其余 403；读端点不鉴权 | 卡只要求「非授权主体创建→403」，需要一条能稳定触发 403 的可判定规则 |
| 4 | 授权实现位置 | 控制器内判定 + 自定义异常 + @RestControllerAdvice，未用 Spring Security | 卡未点名安全框架；最小 Spring 官方依赖即可满足 403 + 错误体要求 |
| 5 | traceId 来源 | 错误处理器内生成 UUID 回填错误体（无 tracing 中间件、不读入站头） | 卡只要求错误体「含 traceId」，未定来源 |
| 6 | 缓存实现 | Spring Cache 抽象：@EnableCaching + ConcurrentMapCache，键=tenant:id，负结果（null）不缓存 | 卡要求读走缓存 + 租户不得读他人缓存副本；spring-boot-starter-cache 是官方 starter |
| 7 | 「读不到」的具体状态码 | 404 | 卡只限定「非 200 非 500」 |
| 8 | 验收 1 说「第 2～6 条行为各有测试断言」 | 第 6 条是登记四个数的元任务、无服务运行时行为 → 测试覆盖 2～5（另加探针端点可见性测试），第 6 条以本报告自证 | 第 6 条内容本身不可被服务行为测试断言 |
| 9 | Spring Boot 版本 | 3.5.14（3.5 稳定线、~/.m2 已缓存该 parent；4.x 行为变化大，对照组求稳） | 卡未指定版本 |
| 10 | 端点路径与 id 形态 | POST/GET /books、/books/{id}；UUID 字符串作 id | 卡只说「创建/读取两个端点」，未定路径、资源名与 id 形态 |
| 11 | 触达次数的暴露方式 | GET /internal/storage-stats 端点（外部可观测）+ 测试内直读 bean 双通道 | 卡要求「必须可观测触达次数」，未定暴露形式 |

环境操作注记（非歧义，卡已明说「自备」）：Maven 经 Central 发行包取得 3.9.9；该下载弯路约占 555s 中的 9 分钟。

## pom 依赖列表
- parent：org.springframework.boot:spring-boot-starter-parent:3.5.14
- org.springframework.boot:spring-boot-starter-web（compile）
- org.springframework.boot:spring-boot-starter-cache（compile）
- org.springframework.boot:spring-boot-starter-test（test）
- 插件：spring-boot-maven-plugin（版本随 parent）
- 说明：Jackson 为 starter-web 传递依赖，非显式引入；全项目无任何非 Spring 官方显式依赖。

## 启动后真实请求与响应（java -jar，端口 18080，fresh JVM，探针从 0 起；id=b919b8fa-c529-4bc0-85f5-1ff5b7d61f33）

### §1 读前探针 → 0
```
GET /internal/storage-stats
HTTP/1.1 200
{"storageReads":0}
```

### §2 授权写者创建 → 201 + 审计字段（验收 2）
```
POST /books  -H "X-Tenant-Id: acme" -H "X-User: alice" -d {"title":"Dune","author":"Frank Herbert"}
HTTP/1.1 201
{"id":"b919b8fa-c529-4bc0-85f5-1ff5b7d61f33","tenant":"acme","title":"Dune","author":"Frank Herbert","createdBy":"alice","createdAt":"2026-10-26T13:34:17.273691Z"}
```

### §3 非授权主体 mallory → 403 机器可读（验收 3）
```
POST /books  -H "X-Tenant-Id: acme" -H "X-User: mallory"
HTTP/1.1 403
{"error":"ACCESS_DENIED","message":"principal mallory is not allowed to create books","traceId":"01b328e0-fe7e-407c-880a-da7fb5c346f1","status":403,"timestamp":"2026-10-26T13:34:17.293064Z"}
```

### §4 acme 首读 → 200（缓存 miss → 触达 #1）
```
GET /books/b919b8fa-...  -H "X-Tenant-Id: acme"
HTTP/1.1 200
{"id":"b919b8fa-...","tenant":"acme","title":"Dune","author":"Frank Herbert","createdBy":"alice","createdAt":"2026-10-26T13:34:17.273691Z"}
```

### §5 首读后探针 → 1
```
GET /internal/storage-stats → HTTP/1.1 200 {"storageReads":1}
```

### §6 acme 二读 → 200，同一响应体（缓存命中）

### §7 二读后探针 → 仍为 1（验收 4：两次读只触达一次）
```
GET /internal/storage-stats → HTTP/1.1 200 {"storageReads":1}
```

### §8 租户 globex 读同一 id → 404，无 A 数据泄漏（验收 5）
```
GET /books/b919b8fa-...  -H "X-Tenant-Id: globex"
HTTP/1.1 404
{"error":"BOOK_NOT_FOUND","message":"book b919b8fa-... not found in tenant globex","traceId":"8bd6ca36-3ae8-42ff-95d0-d121f5c140e8","status":404,"timestamp":"2026-10-26T13:34:17.347683Z"}
```

### §9 终态探针 → 2
B 的 404 是 B 键下的缓存 miss（走存储 +1，而非命中 A 的缓存副本）；随后 acme 再读 200 且探针不增（A 缓存项未受污染）。
```
GET /internal/storage-stats → HTTP/1.1 200 {"storageReads":2}
```

## 复跑步骤
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
cd /tmp/leitu-control-02/work/book-catalog
/tmp/leitu-control-02/tools/apache-maven-3.9.9/bin/mvn -B test          # 预期：Tests run: 5, Failures: 0, Errors: 0
/tmp/leitu-control-02/tools/apache-maven-3.9.9/bin/mvn -B -DskipTests package
$JAVA_HOME/bin/java -jar target/book-catalog-0.0.1-SNAPSHOT.jar --server.port=18080
# 然后按上文 §1~§9 的 curl 重放（新 id 需由 §2 创建获得）
```

## 验证分层与未覆盖项
- 跑过并看到结果：首次 `mvn test`（5/5 绿）；打包后真机启动 + 9 组 curl 转录（本报告全部 HTTP 证据）。
- 只读代码判断：无（关键行为均有运行证据）。
- 没验证：① 并发场景（多线程同时冷读同一 id 的竞态/击穿）——单线程 MockMvc 与单发 curl 未覆盖；② 应用重启后缓存语义（进程内缓存预期清空，未实测）；③ 真实关系库路径——按卡允许选择进程内存储规避；建议父 agent 如需可补并发读测试。
