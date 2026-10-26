package com.example.bookcatalog;

import cn.youhuale.leitu.adapter.web.FailureNoticeException;
import cn.youhuale.leitu.capability.cache.model.CachePolicy;
import cn.youhuale.leitu.capability.cache.spi.Cache;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.failure.model.FailureNotice;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books")
public class BookController {

    private final DataStore<Book, String> books;
    private final Cache<String, Book> cache;
    private final CachePolicy policy;
    private final GuardChain chain;
    private final ExecutionContextReader who;

    public BookController(DataStore<Book, String> books, Cache<String, Book> cache, CachePolicy policy,
                          GuardChain chain, ExecutionContextReader who) {
        this.books = books;
        this.cache = cache;
        this.policy = policy;
        this.chain = chain;
        this.who = who;
    }

    @Operation(operationId = "book.create", summary = "创建图书")
    @PostMapping
    public Book create(@RequestParam String id, @RequestParam String title, @RequestParam long priceCents) {
        Decision decision = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "book.create", "-"));
        if (decision.isDeny()) {
            throw FailureNoticeException.from(decision, who);
        }
        return books.save(Book.create(id, title, priceCents));
    }

    /** 读取走 getOrLoad 装载收编：同键并发只装载一次；读不到是业务错（不缓存空）。 */
    @Operation(operationId = "book.get", summary = "读取图书")
    @GetMapping("/{id}")
    public Book get(@PathVariable String id) {
        return cache.getOrLoad(id, key -> books.findById(key).orElseThrow(() -> new FailureNoticeException(
                        FailureNotice.business("book.not-found", "图书不存在",
                                "图书 " + key + " 不存在或已删除；请确认编号后重试", who.current()))),
                policy.ttl());
    }
}
