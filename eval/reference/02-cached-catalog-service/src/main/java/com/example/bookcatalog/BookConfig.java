package com.example.bookcatalog;

import cn.youhuale.leitu.adapter.jdbc.JdbcAudit;
import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.adapter.jdbc.JdbcMapping;
import cn.youhuale.leitu.capability.cache.api.Caches;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 装配：存取器（计数装饰，探针用）+ 缓存（装配处显式分支，决策可见）+ 守卫声明即收编。
 * 对照 docs/problems/cache.md 与 examples/cache 的姿势。
 */
@Configuration
public class BookConfig {

    static final JdbcMapping<Book, String> BOOK_MAPPING = JdbcMapping.<Book, String>builder()
            .table("books")
            .columns("title", "price_cents")
            .idOf(Book::id)
            .values(b -> List.of(b.title(), b.priceCents()))
            .rowMapper((rs, rowNum) -> new Book(
                    rs.getString("id"), JdbcAudit.fields(rs),
                    rs.getString("title"), rs.getLong("price_cents")))
            .build();

    @Bean
    CountingDataStore countingBooks(JdbcDataStoreFactory factory) {
        return new CountingDataStore(factory.create(BOOK_MAPPING));
    }

    /** 业务侧拿到的是计数装饰后的存取器——控制器与测试看到同一个计数真相。 */
    @Bean
    DataStore<Book, String> books(CountingDataStore counting) {
        return counting;
    }

    /** 缓存实例按用途命名、装配处显式分支（enabled ? inMemory : noop）——不做全局兜底自动装配。 */
    @Bean
    Cache<String, Book> bookCache(CachePolicy policy) {
        return policy.enabled()
                ? Caches.inMemory(policy.maxSize())
                : Caches.noop();
    }

    /** 演示守卫：book.create 仅 alice 放行（action 与 operationId 点分同源）。 */
    @Bean
    Guard bookCreateGuard() {
        return request -> "book.create".equals(request.action()) && !"alice".equals(request.subject())
                ? Decision.deny("仅 alice 可创建图书（探针守卫）", Retry.never())
                : Decision.allow();
    }
}
