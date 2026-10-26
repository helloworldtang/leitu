# 接入清单：从空目录到一个能跑的服务

> **这一页的顺序就是阅读顺序。** 接入所需的事实原先散在 README「在业务项目中接入」段、问题页、金样本三处，
> 新人和 AI 各自去拼——第一次独立采样里 20 个歧义点有 15 个是「文档写了、但没被找到」。
> 所以这里按**执行顺序**合并成一页；每节末尾的「依据」给出细节所在，这一页没写到的再去读那一处。
>
> 适用范围：Spring Boot 业务项目（宿主在前，累土在后）。只想读懂本仓库，读 [README](../README.md) 的「快速开始」。

## 0. 三条前置（缺一条就在编译期踩坑）

| 前置 | 怎么写 | 漏了会怎样 |
|---|---|---|
| **JDK 21** | 本机与 CI 都用 21 | 低版本 JVM 直接 `UnsupportedClassVersionError` |
| **`maven.compiler.release=21`** | 写进业务项目自己的 `<properties>`——**BOM import 只接管依赖版本，不继承 properties** | JDK 21 下报 `找不到符号: 类 var` |
| **`-parameters`** | `maven-compiler-plugin` 的 `<parameters>true</parameters>`（Spring 应用标准配置） | 控制器方法参数名丢失，绑定报错 |

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
</properties>

<build><plugins><plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration><parameters>true</parameters></configuration>
</plugin></plugins></build>
```

依据：[README](../README.md)「在业务项目中接入」段的两条前置说明。

## 1. 依赖（照抄四个坐标，版本全交给 BOM）

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>cn.youhuale</groupId>
      <artifactId>leitu-parent</artifactId>
      <version>0.2.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- ① 累土：引它即拿到全部已交付答案的默认装配（用户 Bean 优先） -->
<dependency>
  <groupId>cn.youhuale</groupId>
  <artifactId>leitu-spring-boot-starter</artifactId>
</dependency>

<!-- ② "标准 Spring 组合" = Web + JDBC + 驱动，一个都不能少 -->
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
```

**要 `/v3/api-docs` 与 `/swagger-ui`，必须自己加 springdoc**——starter 把它与 knife4j 标为 `optional`，
不替使用者决定要不要那几十 MB 的 Swagger UI 全家桶；未引时 `LeituOpenApiAutoConfiguration` 不加载、端点不出现。
`doc.html` 是 knife4j 的界面，只引 springdoc 不会出现：

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
<!-- 可选：中文界面 -->
<dependency>
  <groupId>com.baizhukui</groupId>
  <artifactId>knife4j-openapi3-boot4-spring-boot-starter</artifactId>
</dependency>
```

测试侧另加 `spring-boot-starter-test`（Boot 4 的 MockMvc 在 `spring-boot-webmvc-test`，两个都要）。
**Boot 4 宿主还需在 `dependencyManagement` 里 import `junit-bom` 6.x**——根 BOM 自 0.2.1 起不再携带它，
不引就可能被压回 JUnit 5，测试基建直接 `NoSuchMethod`。

版本一律交给 BOM，**不要手写版本号**——`leitu-parent` 同时 import 了 `spring-boot-dependencies`，Boot 的版本也从这里来。
但 `spring-boot-maven-plugin` 的 `version` 必须自己写（BOM 不管插件），比如 `${spring-boot.version}`；
不然 `spring-boot:run` 会报缺版本。

**BOM import 与 `<parent>` 是二选一，不要叠加**：`<parent>` 继承姿势自 0.2.1 起可用（0.2.0 只能 import BOM），
顺带拿到 compiler / surefire / enforcer 的配置；**项目在 leitu 仓库之外时必须写 `<relativePath/>`**，
否则 Maven 会按默认的 `../pom.xml` 去找父工程。两个都写会得到两套版本管理，优先级靠猜——别试。
用 `<parent>` 时**记得显式写自己的 `<version>`**——不写就继承 leitu 的 0.2.1，自己的服务顶着库的版本号，
发布时才知道有多困惑。

## 2. 配置属性（框架只读这三个）

| 键 | 默认 | 作用 |
|---|---|---|
| `leitu.openapi.title` | `服务 API` | OpenAPI 文档标题 |
| `leitu.openapi.version` | `0.0.0` | 文档里的 API 版本——**填你自己的服务版本**（如 `0.1.0`），与 leitu 库版本无关 |
| `leitu.openapi.description` | 内置一句 | 文档描述 |

配置来源固定三源兜底：**系统属性 → 环境变量 → `leitu.properties`**，读时求值；秘密（凭据）走 SPI，
不进普通配置、不落日志。属性只在引了 springdoc 且自动配置加载后生效。
**`leitu.properties` 不必建**——文件不存在是常态（空源），要建就放 classpath 根。

依据：[config-source](problems/config-source.md)。

## 3. 建表（六列固定，列名 snake_case，别改名）

```sql
CREATE TABLE IF NOT EXISTS orders (
  tenant       VARCHAR(64)  NOT NULL,   -- 作用域，不是实体字段
  id           VARCHAR(64)  NOT NULL,
  created_by   VARCHAR(128) NOT NULL,   -- 审计四件套，存取器盖章
  created_at   TIMESTAMP    NOT NULL,
  updated_by   VARCHAR(128) NOT NULL,
  updated_at   TIMESTAMP    NOT NULL,
  amount_cents BIGINT       NOT NULL,   -- 业务列从第七列开始
  PRIMARY KEY (tenant, id)
);
```

两条硬规则：**主键是 `(tenant, id)` 联合键**（跨租户同主键是两行）；**业务含义需要同名字段时改名**
（如 `tenant_code` / `created_by_name`），否则与约定列冲突——adapter 会带着说明报错。

建表脚本放 `src/main/resources/schema.sql`：嵌入式库（H2）由 Boot 自动执行；外部库（MySQL 等）
Boot 默认不跑，得自己上迁移工具或显式开 `spring.sql.init.mode=always`。

依据：[data-access](problems/data-access.md)；可运行对照 `examples/spring-boot` 的 `schema.sql`。

## 4. 绑定操作者（入口一次，全链路只读）

在请求入口用**已认证主体**构造 `Operator` 并绑定；一次绑定，之后处处只读。

三条纪律：

1. **Bean 别叫 `RequestContextFilter`**——与 Boot 内置同名 Bean 冲突，默认配置下会拒绝启动；
2. **没接认证体系时**：照 `examples/spring-boot` 的 `DemoContextFilter` 走演示开关
   `demo.trust-request-headers=true`（读 `X-Demo-User` / `X-Demo-Tenant`，可选 `X-Demo-Trace`），**默认是关的**。
   **开关放测试/演示 profile，不要放进主配置**——放主配置等于把它带进生产。
   关着时一律匿名，而匿名不参与角色判定 → 得到 403，失败方向在安全侧；
3. **请求头不能当身份来源进生产**——调用方可以任意伪造，那等于把门禁拆了。

补一句排序焦虑的解药：**leitu 自己不装 Servlet Filter**，所以你的绑定 Filter 不需要刻意排顺序——
任何 Filter 都天然早于 Controller，绑定在请求进业务前完成即可。

依据：[who-is-operating](problems/who-is-operating.md)。

## 5. 映射、存取器与守卫

```java
// ① 显式映射：表 + 业务列 + 行⇄实体。没有反射，也没有 ORDER_MAPPING 这种现成常量——
//    租户列与审计四列由 adapter 统一管理，columns 里只写你自己的业务列。
static final JdbcMapping<Order, String> ORDER_MAPPING = JdbcMapping.<Order, String>builder()
        .table("orders")
        .columns("amount_cents")
        .idOf(Order::id)
        .values(o -> List.of(o.amountCents()))
        .rowMapper((rs, rowNum) -> new Order(
                rs.getString("id"), JdbcAudit.fields(rs), rs.getLong("amount_cents")))
        .build();

// ② 存取器一行声明
@Bean
DataStore<Order, String> orders(JdbcDataStoreFactory factory) {
    return factory.create(ORDER_MAPPING);
}

// ③ 守卫声明即收编（示例语义：order.create 只有 alice 放行——与自检里 alice 拿到 200 对齐）
@Bean
Guard orderCreateGuard() {
    return request -> "order.create".equals(request.action()) && !"alice".equals(request.subject())
            ? Decision.deny("仅 alice 可创建订单（演示守卫）", Retry.never())
            : Decision.allow();
}
```

守卫声明即收编——写一个 `Guard` Bean 就进判定链，其余装配自动完成（用户 Bean 优先）。
内存兜底 `DataStores.inMemory(...)` 仅用于测试与无库场景，重启即失。

`columns(...)` 与 `values(...)` **按位置一一对应**（列表顺序即列顺序），不靠列名匹配——多列时自己对齐。

**收口有两种，别混着写**：

| 收口位置 | 写法 | 适合 |
|---|---|---|
| 存取缝 | `DataStores.guarded(store, chain, "order", Order::id)` | 读写都要判（四个 `data:*` 动作自动带上），业务代码看不见判定 |
| 控制器 | 自己 `chain.check(AccessRequest...)` 再处理 `Decision` | 端点语义与动作名自定，需要把判定结果转成业务失败 |

两种都是标准答案，但**一个服务里只选一种**：混用会让同一个动作有时被判、有时跳过。

## 6. 读不到怎么交代（最常猜错的一节）

| 情形 | 存取器行为 | 建议投影 |
|---|---|---|
| 他租户读同一 id | 返回 `empty`（**不泄漏、不报错**） | 单资源路径用 **404** + `<域>.not-found`（要统一 400 也行，一个服务只能一种） |
| 本租户无此行 | 返回 `empty` | 同上 |
| 判定链否决（`GuardedDataStore` 内部抛） | 抛 `AccessDeniedException`，**不降级为空结果** | 403 `guard.denied`，由 `FailureAdvice` 统一投影 |
| 控制器自己 `chain.check` 得到 deny | 返回 `Decision`，不抛 | 你自己调 `FailureNoticeException.from(decision, who)` 抛出 |
| 未绑定操作者 | 匿名，不参与角色判定 | 403（失败方向在安全侧） |

**deny-by-default 到底管什么**（最常误读的一条）：它只指**空链**——一个 Guard 都没装配时一律拒；
而「有 Guard 但不匹配这个 action」会返回 **allow**（匿名仍然拒）。所以别指望链替你拦住未覆盖的动作：
哪个动作要判，就得有 Guard 显式认领它。

资源名用法：`AccessRequest.resource` 填数据主键；创建时还没有主键，传 `"-"`（占位，不是空字符串）。

**`type` 有两层，别混**（混了要到首次运行才炸）：

| 层 | 写法 | 谁负责 |
|---|---|---|
| 业务代码构造失败时 | **裸点分** `order.not-found`——字符集 `[A-Za-z0-9]+([._-][A-Za-z0-9]+)*`（**不含冒号**），构造期校验 | 你 |
| 出网的响应体里 | **完整 URN** `urn:leitu:problem:order.not-found` | adapter-web 投影时加前缀 |

字符集里没有冒号是**故意的**：core 不知道传输层要加什么前缀，URN 化只发生在投影边界。
所以自检断言的是响应体（URN 形态）、代码里写的是点分标识——两者都对，不是矛盾。

依据：[failure-response](problems/failure-response.md)、[data-access](problems/data-access.md)。

## 7. 对外契约（引了 springdoc 才有）

- **`data:*` 四个动作属于存取缝**：`data:save` / `data:read` / `data:delete` / `data:list`，只在用
  `DataStores.guarded(...)` 包存取器时出现，资源名 = `域/主键`（列表为域级）；
- **业务端点自己定动作名**，点分即可（`order.create` / `book.get`）——不要借 `data:*` 当业务动作名；
- **`operationId` 与动作名同源是约定，不是机制**：框架不会把两处字符串自动对齐，写错不会报错——
  两处都从同一个常量取，才是可靠做法；
- **端点豁免**：`/v3/api-docs`、`/swagger-ui`、`/doc.html`、`/webjars`、`/swagger-resources` 不参与无差别包装。

依据：[api-contract](problems/api-contract.md)。

## 8. 自检（跑一次构建 + 打三个请求）

```bash
mvn test                                   # 首跑即绿才算「一次绿灯」；至少一个测试盖住下面三条行为，
                                           # 零测试时 surefire 也会绿——那种绿不算数
curl -X POST 'localhost:8080/orders?amountCents=100' \
  -H 'X-Demo-User: alice' -H 'X-Demo-Tenant: t1'
# 期望 200，且 createdBy = alice（审计盖章）
curl -X POST 'localhost:8080/orders?amountCents=100' \
  -H 'X-Demo-User: bob' -H 'X-Demo-Tenant: t1'
# 期望 403 + application/problem+json + "type":"urn:leitu:problem:guard.denied" + X-Trace-Id
curl 'localhost:8080/orders/<id>' -H 'X-Demo-User: alice' -H 'X-Demo-Tenant: t2'
# 期望 400/404 业务错（他租户读不到），而不是 200 也不是 500
```

路径与参数形态按你自己的控制器签名写（金样本用 `@RequestParam`）；上面三条只规定**行为**，不规定 URL。
两个没规定的也不必猜：**创建成功码**金样本用 200，要用 201 也行，全服务统一即可；
**id 由谁生成**是业务决定（金样本让调用方传，服务端生成就用 UUID）——验收只看行为，不看这一层。

三条都过，说明管道、判定、隔离、失败投影四段都接上了。

## 9. 只在这一页不够时，按这个顺序补读

1. 本页；
2. 对应问题的**「二、机制」与「四、边界」两段**——不要通读，问题页是给人做判断的，不是给接入的；
3. `examples/<答案id>` 的金样本——照写，不要发明新姿势；
4. 仍不清楚，说明这一页缺了一条，补进来（每一条都来自真实踩坑）。

## 常见踩坑（均来自真实采样）

- 以为 `deny-by-default` 会拦住“没写 Guard 的动作” → 它只管**空链**；有 Guard 但不匹配就放行；
- 把 `data:*` 当业务动作名 → 那是 guarded 存取器的四个固定动作，业务端点自己命名；
- 只引 springdoc 就找 `doc.html` → 那是 knife4j 的界面；
- 把演示开关 `demo.trust-request-headers` 放主配置 → 等于把“请求头当身份”带进生产；
- 业务代码里把 `type` 写成 `urn:leitu:problem:...` → 字符集不含冒号，构造期就抛；URN 由投影层加；
- 以为 `ORDER_MAPPING` 是库里的常量 → 它是你要自己写的一个 `JdbcMapping` 字段（见第 5 节）；
- `找不到符号: 类 var` → 没写 `maven.compiler.release=21`；
- `/v3/api-docs` 404 → 没自己引 springdoc（starter 标了 optional）；
- 实体上加了 `tenant` 字段 → 租户是作用域不是字段，两个真相必然打架；
- 动作名在 `@Operation` 与 `chain.check` 两处手写 → 漂移不会报错，抽成常量；
- 入口 Filter 取名 `RequestContextFilter` → 与 Boot 内置 Bean 撞名，启动失败。
