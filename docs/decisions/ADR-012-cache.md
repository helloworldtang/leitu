# ADR-012 问题"怎么缓存"的答案：TTL+LRU 进程内缓存 + getOrLoad 装载收编 + 租户作用域键

状态：已接受（2026-09-13）

## 背景

缓存是最高频的性能答案，也是漂移最重的一处。两条已答问题在此汇合：缓存参数（上限/默认 TTL/开关）从配置来（config-source）；命中与装载是事件源（what-happened）。对常见缓存实践验尸得四条（独立论证）：

1. **驱逐策略在抽象层缺失**=知识空缺——容量上限没人承诺，每个实现自己踩内存膨胀；
2. **装载三段式 get→miss→put 手写漂移**——并发同键装载 N 次（击穿），每处调用方一份三段式；
3. **注解自动装饰（@Cacheable 式）**——装饰是否在环、键是什么、TTL 多少全不可见，本库第三次拒绝注解式（allow-or-not、input-validation 之后）；
4. **键前缀拼租户靠人肉记得**——忘一次即跨租户串数据。

## 决策

标准答案 = **Cache 缓存缝 + TTL+LRU 进程内默认实现 + 装载收编**：

- **TTL + LRU 都进 v1**：访问序 LinkedHashMap + removeEldestEntry，读时惰性过期（无后台线程），每方法粗粒度互斥（synchronized 同一把锁）——正确性一句话可证
- **TTL 必须显式**：put / getOrLoad 携带正 Duration；项目级默认走 `CachePolicy.fromConfig`（cache.max-size / cache.ttl / cache.enabled，缺省 1000 / PT30M / true）——配置来的显式默认，不藏在实现里
- **getOrLoad 收编三段式**：SPI default 方法组合（一切实现免费获得）+ 进程内覆写为同键并发只装载一次（防击穿）；**分布式实现不承诺装载原子**——跨进程互斥是 lock 能力的事（catalog 条目 lock）
- **租户作用域键**：业务传裸键，实现内部按上下文租户复合；"-" 非通配（系统参考数据按租户各载一份=安全重复）；上限是实例级（跨租户合计，按租户上限=每租户一台的装配策略）
- **观察 opt-in**：5 事件（cache.hit / cache.miss / cache.put / cache.evict.hit / cache.evict.miss，attributes 带 cache.key）；**装饰器必须覆写 getOrLoad**——先 get 发事件、未命中走 delegate.getOrLoad（原子留在 delegate 锁内），否则装饰在锁外二次 get-miss 会击穿只装一次承诺
- **Noop=无害维度默认值的兑现**：enabled=false 的装配答案是 `Caches.noop()`（零开销禁用；参数合同不因 Noop 松动）
- **evict 返回 boolean**（与 DataStore.deleteById 同构：命中=true 未见=false）；前缀驱逐与全量清空属管理面，v1 不入

## 业界对照

Caffeine / Guava Cache（expireAfterWrite + 容量驱逐的正典——内存实现的最小同构）；Redis EXPIRE / maxmemory（分布式对应物）；Spring Cache 抽象（Cache + CacheManager 多实现同构=可替换性对照；差异：显式工厂装配，拒注解自动代理）；ConcurrentHashMap.computeIfAbsent（装载原子语义的参照）。不取操作集式命名（…Operations 后缀）：本答案是能力面，物名 Cache（术语三关第三关）。

## 后果

- adapter 接缝明确：实现 Cache——TTL→EXPIRE、键命名空间按租户教义、值类型编解码（序列化）是 adapter 扩展不进缝
- rules 资产扩展第三条参数化常量（INTERNAL_只被本能力模块访问_缓存）
- 粗粒度互斥的性能边界：装载期间阻塞本缓存一切操作——高并发装载出路是 adapter 或 lock 能力（文档写明）
- 惰性过期占内存直到被读或被 LRU 驱逐——上限兜底

**被拒绝的备选**：无上限缓存（内存 footgun）；隐藏默认 TTL（漂移源）；注解自动装饰（第三次拒绝）；分布式装载互斥进 cache（越权——lock 的事）；键前缀人肉租户（作用域教义）；前缀驱逐与全量清空进 v1（管理面 YAGNI）；per-key 锁条带（v1 复杂度不值）；`get(key, Class)` 进缝（反序列化是 Redis adapter 的事）；always-on 观测（第一处隐藏自动记录，与全库显式装配相悖）。
