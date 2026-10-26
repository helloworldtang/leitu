package com.example.bookcatalog;

import cn.youhuale.leitu.capability.data.spi.DataStore;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 计数装饰器：包住真存取器，让「第二次读取不再触库」可以被测试断言。
 *
 * <p>这是评测探针的观测手段（缓存命中证据），不是业务代码——真实项目用
 * {@code Caches.observing(...)} 的 cache.hit / cache.miss 事件看同样的真相。
 */
public final class CountingDataStore implements DataStore<Book, String> {

    private final DataStore<Book, String> delegate;
    private final AtomicLong reads = new AtomicLong();

    public CountingDataStore(DataStore<Book, String> delegate) {
        this.delegate = delegate;
    }

    /** findById 的触库次数（不含 save / delete）。 */
    public long reads() {
        return reads.get();
    }

    @Override
    public Book save(Book entity) {
        return delegate.save(entity);
    }

    @Override
    public Optional<Book> findById(String id) {
        reads.incrementAndGet();
        return delegate.findById(id);
    }

    @Override
    public boolean deleteById(String id) {
        return delegate.deleteById(id);
    }

    @Override
    public java.util.List<Book> findAll() {
        return delegate.findAll();
    }

    @Override
    public java.util.List<Book> findAll(cn.youhuale.leitu.capability.data.model.PageRequest page) {
        return delegate.findAll(page);
    }
}
