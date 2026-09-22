package cn.youhuale.leitu.capability.data;

import cn.youhuale.leitu.capability.data.api.DataStores;
import cn.youhuale.leitu.capability.data.model.AuditFields;
import cn.youhuale.leitu.capability.data.model.Auditable;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.context.api.ExecutionContextBinders;
import cn.youhuale.leitu.core.context.api.ExecutionContextReader;
import cn.youhuale.leitu.core.context.model.ExecutionContext;
import cn.youhuale.leitu.core.context.model.Operator;
import cn.youhuale.leitu.core.guard.api.AccessDeniedException;
import cn.youhuale.leitu.core.guard.api.GuardChain;
import cn.youhuale.leitu.core.guard.model.AccessRequest;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据权限装饰器：把"数据权限走判定链"从自觉变成机制。
 *
 * <p>反向自测性质：若哪天装饰器被改成"否决时静默返回空"，{@link #否决不降级为不存在_抛异常而非空结果}
 * 必须变红——那是把"没权限"伪装成"不存在"的事故配方。
 */
class GuardedDataStoreTest {

    record Note(String id, AuditFields auditFields) implements Auditable {
        static Note create(String id) {
            return new Note(id, AuditFields.empty());
        }

        @Override
        public Note withAuditFields(AuditFields auditFields) {
            return new Note(id, auditFields);
        }
    }

    private static final cn.youhuale.leitu.core.context.spi.ExecutionContextBinder BINDER =
            ExecutionContextBinders.threadLocal();

    private DataStore<Note, String> guarded(GuardChain chain) {
        return DataStores.guarded(DataStores.inMemory(Note::id), chain, "note", Note::id);
    }

    @Test
    void 放行时读写照常落库() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = guarded(GuardChain.of(request -> Decision.allow()));
            store.save(Note.create("n-1"));
            assertTrue(store.findById("n-1").isPresent());
        }
    }

    @Test
    void 否决时写被拦下且不落库() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> delegate = DataStores.inMemory(Note::id);
            DataStore<Note, String> store = DataStores.guarded(delegate, GuardChain.of(
                    request -> Decision.deny("测试否决：alice 不可写 note", Retry.never())), "note", Note::id);
            AccessDeniedException e = assertThrows(AccessDeniedException.class,
                    () -> store.save(Note.create("n-1")));
            assertTrue(e.getMessage().contains("测试否决"));
            assertTrue(delegate.findById("n-1").isEmpty(), "否决即拦下：底层一行都没写");
        }
    }

    @Test
    void 否决不降级为不存在_抛异常而非空结果() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = guarded(GuardChain.of(
                    request -> Decision.deny("测试否决：无权读 note", Retry.never())));
            assertThrows(AccessDeniedException.class, () -> store.findById("n-1"),
                    "无权访问 ≠ 不存在：静默返回 empty 会把越权伪装成空结果");
        }
    }

    @Test
    void 空判定链_一切读写被否决_deny_by_default() {
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = guarded(GuardChain.of());
            assertThrows(AccessDeniedException.class, () -> store.save(Note.create("n-1")));
            assertThrows(AccessDeniedException.class, () -> store.findAll());
        }
    }

    @Test
    void 判定请求带主体动作与资源_资源含主键() {
        List<AccessRequest> seen = new ArrayList<>();
        try (var scope = BINDER.bind(ExecutionContext.of(Operator.human("alice", "tenant-a"), "t-1"))) {
            DataStore<Note, String> store = guarded(GuardChain.of(request -> {
                seen.add(request);
                return Decision.allow();
            }));
            store.save(Note.create("n-1"));
            store.findById("n-1");
            store.deleteById("n-1");
            store.findAll();
        }
        assertEquals(List.of("data:save", "data:read", "data:delete", "data:list"),
                seen.stream().map(AccessRequest::action).toList());
        assertEquals("alice", seen.get(0).subject());
        assertEquals("note/n-1", seen.get(0).resource());
        assertEquals("note", seen.get(3).resource(), "列表是域级判定");
        assertTrue(seen.stream().allMatch(r -> r.direction() == AccessRequest.Direction.INBOUND));
    }

    @Test
    void 允许的判定不能被抛_装配错误大声失败() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new AccessDeniedException(Decision.allow(), ExecutionContext.of(Operator.system(), "t-1")));
        assertTrue(e.getMessage().contains("放行"), "异常必须即教程：" + e.getMessage());
    }

    @Test
    void 读取器可注入_自定义绑定机制下主体正确() {
        ExecutionContextReader custom = () -> ExecutionContext.of(Operator.agent("agent-7", "tenant-z"), "t-9");
        List<AccessRequest> seen = new ArrayList<>();
        DataStore<Note, String> store = DataStores.guarded(
                DataStores.inMemory(Note::id, custom, java.time.Clock.systemUTC()),
                GuardChain.of(request -> {
                    seen.add(request);
                    return Decision.allow();
                }), "note", Note::id, custom);
        store.save(Note.create("n-1"));
        assertEquals("agent-7", seen.get(0).subject(), "主体取注入的读取器，不是全局线程绑定");
    }
}
