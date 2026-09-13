package cn.youhuale.leitu.capability.data;

import cn.youhuale.leitu.capability.data.api.DataStores;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.observe.api.ObservationRecorder;
import cn.youhuale.leitu.core.observe.model.ObservationEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/** data 能力的单测：锁审计盖章、租户作用域与内存兜底语义。 */
class DataStoresTest {

    /** 本地测试实体：record 一行 wither 实现 Auditable（金样本同款模式）。 */
    record Note(String id, AuditFields auditFields, String body) implements Auditable {
        static Note create(String id) {
            return new Note(id, AuditFields.empty(), "正文");
        }

        @Override
        public Note withAuditFields(AuditFields auditFields) {
            return new Note(id, auditFields, body);
        }
    }

    /** 可拨时钟：审计时刻可断言。 */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-13T00:00:00Z");

        void tick() {
            now = now.plusSeconds(90);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final ExecutionContextReader who = ExecutionContextReader.threadLocal();
    private final MutableClock clock = new MutableClock();

    /** 测试共用的绑定器：try-with-resources 开作用域。 */
    private static final cn.youhuale.leitu.core.context.spi.ExecutionContextBinder BINDER =
            ExecutionContextBinders.threadLocal();

    private DataStore<Note, String> store() {
        return DataStores.inMemory(Note::id, clock);
    }

    @Test
    void 新实体保存_四件套全盖章() {
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            Note saved = store().save(Note.create("n-1"));
            assertEquals("alice", saved.auditFields().createdBy());
            assertEquals("alice", saved.auditFields().updatedBy());
            assertEquals(Instant.parse("2026-09-13T00:00:00Z"), saved.auditFields().createdAt());
            assertEquals(saved.auditFields().createdAt(), saved.auditFields().updatedAt());
        }
    }

    @Test
    void 更新保留创建者_只换修改者与修改时刻() {
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = store();
            store.save(Note.create("n-1"));
            clock.tick();
            Note updated = store.save(Note.create("n-1"));
            assertEquals("alice", updated.auditFields().createdBy(), "createdBy 永不改变");
            assertEquals(Instant.parse("2026-09-13T00:00:00Z"), updated.auditFields().createdAt());
            assertEquals(Instant.parse("2026-09-13T00:01:30Z"), updated.auditFields().updatedAt());
        }
    }

    @Test
    void 行存在性决定插入或更新_实体自带章不作数() {
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = store();
            // 伪造带章实体（真实操作者应为 alice，章里伪造 bob）
            Note forged = new Note("n-1",
                    AuditFields.stampedBy("bob", Instant.parse("2000-01-01T00:00:00Z")), "x");
            Note saved = store.save(forged);
            assertEquals("alice", saved.auditFields().createdBy(), "盖章权在存取器：行不存在=插入章，实体自带章不作数");
            assertEquals(clock.instant(), saved.auditFields().createdAt());
        }
    }

    @Test
    void 审计四件套半空即编程错误() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AuditFields("alice", null, null, null));
        assertTrue(e.getMessage().contains("成对"), "异常必须即教程：" + e.getMessage());
    }

    @Test
    void 未盖章的行不能盖更新章() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> AuditFields.empty().restampedBy("alice", clock.instant()));
        assertTrue(e.getMessage().contains("先落库"), "异常必须即教程：" + e.getMessage());
    }

    @Test
    void 他租户同主键不可见_findById返回空不报错() {
        DataStore<Note, String> store = store();
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            store.save(Note.create("n-1"));
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("mallory", "tenant-b"), "t-2"))) {
            assertTrue(store.findById("n-1").isEmpty(), "他租户行不可见（不泄漏存在性）");
        }
    }

    @Test
    void findAll只见本租户() {
        DataStore<Note, String> store = store();
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            store.save(Note.create("a-1"));
            store.save(Note.create("a-2"));
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("bob", "tenant-b"), "t-2"))) {
            store.save(Note.create("b-1"));
            assertEquals(1, store.findAll().size(), "tenant-b 只见自己的一行");
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertEquals(2, store.findAll().size(), "tenant-a 只见自己的两行");
        }
    }

    @Test
    void 系统作用域_横杠不是全局通配() {
        DataStore<Note, String> store = store();
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.system(), "t-sys"))) {
            store.save(Note.create("sys-1"));
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertTrue(store.findById("sys-1").isEmpty(), "系统行不对租户可见——\"-\"不是通配");
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.system(), "t-sys"))) {
            assertTrue(store.findById("sys-1").isPresent(), "\"-\" 上下文可见自己的行");
        }
    }

    @Test
    void 删除作用域内命中返回true_再读为空() {
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = store();
            store.save(Note.create("n-1"));
            assertTrue(store.deleteById("n-1"));
            assertTrue(store.findById("n-1").isEmpty());
            assertFalse(store.deleteById("n-1"), "再删=未见=false");
        }
    }

    @Test
    void 他租户删除同主键返回false_原行无恙() {
        DataStore<Note, String> store = store();
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            store.save(Note.create("n-1"));
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("mallory", "tenant-b"), "t-2"))) {
            assertFalse(store.deleteById("n-1"), "他租户删除=未见=false，不报错");
        }
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            assertTrue(store.findById("n-1").isPresent(), "原行无恙");
        }
    }

    @Test
    void null入参必填教学() {
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = store();
            NullPointerException e1 = assertThrows(NullPointerException.class, () -> store.save(null));
            assertTrue(e1.getMessage().contains("必填"));
            NullPointerException e2 = assertThrows(NullPointerException.class, () -> store.findById(null));
            assertTrue(e2.getMessage().contains("必填"));
            NullPointerException e3 = assertThrows(NullPointerException.class, () -> store.deleteById(null));
            assertTrue(e3.getMessage().contains("必填"));
        }
    }

    @Test
    void 并发保存不丢行() throws InterruptedException {
        DataStore<Note, String> store = store();
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                final int prefix = t;
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        // ThreadLocal 绑定不跨线程：工作线程各自绑定同一租户
                        try (var threadScope = BINDER.bind(
                                ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
                            for (int i = 0; i < 50; i++) {
                                store.save(Note.create("p" + prefix + "-" + i));
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                thread.start();
                threads.add(thread);
            }
            start.countDown();
            for (Thread thread : threads) {
                thread.join();
            }
            assertEquals(100, store.findAll().size(), "2 线程 × 50 行不丢（ConcurrentHashMap）");
        }
    }

    @Test
    void 观察装饰器记data事件_锚点带谁与traceId() {
        DataStore<Note, String> store = store();
        List<ObservationEvent> events = new ArrayList<>();
        DataStore<Note, String> observed = DataStores.observing(store, Note::id, recorder(events));
        try (var scope = BINDER.bind(
                ExecutionContext.of(Operator.human("alice", "tenant-a"), "trace-9"))) {
            observed.save(Note.create("n-1"));
            observed.findById("n-1");
            observed.findById("no-such");
        }
        assertEquals(3, events.size());
        assertEquals("data.saved", events.get(0).name());
        assertEquals("data.read.hit", events.get(1).name());
        assertEquals("data.read.miss", events.get(2).name());
        assertEquals("alice", events.get(0).context().operator().subject());
        assertEquals("trace-9", events.get(0).context().traceId());
        assertEquals("n-1", events.get(0).attributes().get("data.id"));
        assertNotNull(events.get(0).duration());
    }

    @Test
    void 内存实现toString大声标注() {
        String text = store().toString();
        assertTrue(text.contains("重启即失"), "大声标注边界：" + text);
        assertTrue(text.contains("adapter"));
    }

    private static ObservationRecorder recorder(List<ObservationEvent> events) {
        return events::add;
    }

}
