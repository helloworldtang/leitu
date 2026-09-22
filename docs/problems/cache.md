# 怎么缓存（cache）

> catalog：capability ｜ required: conditional ｜ status: answered（stability: stable，since 0.2.0） ｜ depends_on: config-source, what-happened

## 一、为什么

缓存是最高频的性能答案，也是漂移最重的一处：TTL 有的传有的不传、容量上限没人设（内存 footgun）、装载三段式每个项目手写（并发同键装载 N 次——击穿）、缓存观测各说各的。两条已答问题在此汇合：缓存参数（上限 / 默认 TTL / 开关）从配置来（config-source）；命中与装载是"发生了什么"的事件源（what-happened）。

没有标准答案时的漂移成本：每个项目自造缓存——手写三段式、并发击穿、无上限膨胀、键前缀拼租户忘一次就跨租户串数据；AI 生成每处缓存都在猜"这个项目 TTL 传不传、上限在哪配、租户怎么隔离"，猜错一次就是一次事故。

## 二、机制（标准答案的形状）

**缓存 = 四操作缝（get / put 显式 TTL / evict / getOrLoad）+ 进程内默认实现（TTL 惰性过期 + LRU 上限）+ 租户作用域键 + opt-in 观察装饰；分布式走 adapter。**

```java
// 已实现：leitu-capability-cache 的 cn.youhuale.leitu.capability.cache 包
public interface Cache<K, V> {
    Optional<V> get(K key);
    void put(K key, V value, Duration ttl);            // TTL 必须显式且为正
    boolean evict(K key);                              // 命中=true，未见=false
    default V getOrLoad(K key, Function<K, V> loader, Duration ttl);  // 装载三段式收编
}

public record CachePolicy(long maxSize, Duration ttl, boolean enabled) {
    static CachePolicy fromConfig(ConfigReader config);  // cache.max-size / cache.ttl / cache.enabled
}                                                        // 缺省 1000 / PT30M / true

public final class Caches {
    static <K, V> Cache<K, V> inMemory(long maxSize);                    // TTL 惰性过期 + LRU + 只装一次
    static <K, V> Cache<K, V> inMemory(long maxSize, Clock clock);       // 测试缝
    static <K, V> Cache<K, V> observing(Cache<K, V> delegate, ObservationRecorder r);
    static <K, V> Cache<K, V> noop();                                    // 零开销禁用（无害维度默认值）
}
```

- **形状**：TTL 显式不藏默认——项目级默认走 `CachePolicy.ttl()`，装配处可见
- **兜底**：进程内实现（访问序 LRU + 惰性过期 + 同键并发只装载一次），toString 自我声明「进程内无序列化——分布式替换走 adapter」
- **插拔**：Redis 等 adapter 实现 Cache（TTL→EXPIRE、键命名空间按租户教义、值编解码是 adapter 扩展）
- **成套答案**：租户作用域键（教义在缓存的落位）；观察 5 事件带 cache.key；Noop 兑现无害维度默认值；CachePolicy.fromConfig 兑现 config 依赖

## 三、取舍

**为什么 TTL + LRU 都进 v1**：生产可用的最小集——无上限缓存是内存 footgun；驱逐策略在抽象层缺失正是既有实践的知识空缺，答案补上不留白。

**为什么 TTL 必须显式**：订单价 30 秒与配置字典 1 小时是两回事；实现内藏默认是决策漂移源——项目级默认=CachePolicy.ttl()，配置来的显式默认。

**为什么 getOrLoad 收编三段式**：get→miss→put 手写是最大漂移点（并发击穿）；进程内承诺同键只装载一次，分布式不承诺——跨进程互斥是 lock 能力的事。

**为什么租户作用域键而不是键前缀**：租户从执行上下文来（单一事实源）——前缀靠人肉记得，忘一次即跨租户串数据；系统参考数据按租户各载一份=安全重复。

**为什么把「装载编排」从存储里拆出来，而不是换个更细的锁**：装载器是用户代码——只要编排靠"持有一把锁"表达，锁就必然横跨装载器执行的整段时间，于是装载器被夹进存储的临界区：一次慢装载冻结整个缓存，两台缓存互相装载则锁序成环。那是角色没拆开，不是锁太粗——**存储不该调用用户代码**。拆开后（存储 / 编排 / 装载器三个角色）编排改用"在途凭证"表达：同键一个领导者装载、其余等待，锁只保护登记与回写这一瞬，永不跨过用户代码。

**为什么成环要侦测而不是等超时**：装载在调用方线程上执行，一旦成环就是线程互等——症状是"接口卡住、CPU 为零、日志全无"。等待前先沿等待图走一圈，走回自己即抛 `CyclicCacheLoadException` 带环路径，让故障表现为一条可读的报错（责任方在装载器里）。

**为什么 Noop 而不是删代码路径**：无害维度可 Noop 是分维度默认值的明文；enabled=false 的装配答案零开销、参数合同不松动（配错在禁用态也 fail-fast）。

**为什么 evict 返回 boolean、没有前缀驱逐与全量清空**：与 DataStore.deleteById 同构（命中=true 未见=false）；前缀驱逐与清空属管理面，等真实痛点。

**被拒绝的备选**：无上限缓存；隐藏默认 TTL；注解自动装饰（第三次拒绝）；分布式装载互斥进 cache；键前缀人肉租户；前缀驱逐与全量清空；per-key 锁条带（与粗粒度互斥一同被"角色拆分"取代）；装载成环靠超时 / 盲等兜底（改为侦测并报环路径）；`get(key, Class)`；always-on 观测。详见 ADR-012。

## 四、边界

- 装载原子性：**进程内承诺（同键只装一次），分布式不承诺**（lock 条目的事）
- 装载期**不阻塞**本缓存任何操作（锁不跨过用户代码）；跨缓存循环依赖不再表现为死锁——被侦测并抛 `CyclicCacheLoadException` 带环路径（值依赖自身=无解，缓存侧不静默等待）
- maxSize 是实例级（跨租户合计）：热键可挤他租户条目；按租户上限=每租户一台的装配策略
- 值语义：进程内存对象引用（无序列化无深拷贝，可变性调用方自负）；Redis adapter 的序列化不进缝
- 过期是惰性的（无后台清扫，上限兜底）
- 观察装饰不记异常路径（failure-response 的地盘）；不记 cache.load——装载耗时由 getOrLoad 路径 cache.put 事件的 duration 给出
- 关联：ADR-012；依赖 config-source / what-happened；金样本 examples/cache
