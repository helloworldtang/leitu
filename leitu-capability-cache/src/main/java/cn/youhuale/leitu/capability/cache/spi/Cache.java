package cn.youhuale.leitu.capability.cache.spi;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 缓存缝——"怎么缓存"的标准答案面（见 ADR-012）。
 *
 * <p>合同（实现者必须守住）：
 * <ol>
 *   <li><b>租户作用域</b>：键在实现内部按当前上下文的租户复合（业务传裸键）；
 *       他租户同键不可见；"-" 是系统级作用域不是全局通配——租户作用域教义在缓存的落位。</li>
 *   <li><b>TTL 必须显式且为正</b>：put / getOrLoad 携带 Duration；项目级默认走
 *       {@code CachePolicy.ttl()}（配置来的显式默认），实现内不藏默认。</li>
 *   <li><b>装载原子性边界</b>：本接口的 getOrLoad 默认实现是 get→miss→装载→put 的组合，
 *       <b>不承诺原子</b>；进程内默认实现覆写为同键并发只装载一次；
 *       分布式实现（Redis adapter）不承诺装载原子——跨进程互斥是 lock 能力的事
 *       （catalog 条目 lock）。</li>
 *   <li><b>null 语义</b>：键 / 值 / TTL / 装载器不收 null（教学异常）；缓存不存 null 值
 *       ——"无值"语义请不缓存（每次装载）。</li>
 * </ol>
 *
 * <p>真库（Redis 等分布式缓存）由 adapter 实现本接口：TTL→EXPIRE、键命名空间按租户教义、
 * 值类型编解码（序列化）是 adapter 的扩展，不进本缝。
 */
public interface Cache<K, V> {

    /** 读当前租户的键；未命中（他租户/缺失/已过期）= Optional.empty()。 */
    Optional<V> get(K key);

    /** 写入（显式 TTL，必须为正）。 */
    void put(K key, V value, Duration ttl);

    /** 驱逐当前租户的键；命中返回 true，未见（他租户/缺失）返回 false。过期未清扫条目被移除也返回 true——清扫即驱逐。 */
    boolean evict(K key);

    /**
     * 装载三段式收编：get→未命中→装载→put。默认实现为组合、不承诺原子；
     * 进程内实现覆写为同键并发只装载一次（防击穿）。装载器抛异常则原样上抛，不缓存不吞。
     */
    default V getOrLoad(K key, Function<K, V> loader, Duration ttl) {
        Objects.requireNonNull(key, "key 必填：缺失语义是 Optional.empty()，不是 null 入参");
        Objects.requireNonNull(loader, "loader 必填：只读不装就改用 get(...)");
        requirePositiveTtl(ttl);
        Optional<V> hit = get(key);
        if (hit.isPresent()) {
            return hit.get();
        }
        V loaded = Objects.requireNonNull(loader.apply(key),
                "装载器不能返回 null：缓存不存 null 值——\"无值\"语义请不缓存（每次装载）");
        put(key, loaded, ttl);
        return loaded;
    }

    /** TTL 前置校验（默认实现与各实现共用）。 */
    static void requirePositiveTtl(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl 必填：TTL 必须显式——项目级默认走 CachePolicy.ttl()");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL 必须为正（ISO-8601 如 PT30S）——零/负 TTL 无缓存语义。当前：" + ttl);
        }
    }
}
