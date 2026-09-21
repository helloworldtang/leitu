package com.example.taskservice;

import cn.youhuale.leitu.adapter.jdbc.JdbcAudit;
import cn.youhuale.leitu.adapter.jdbc.JdbcDataStoreFactory;
import cn.youhuale.leitu.adapter.jdbc.JdbcMapping;
import cn.youhuale.leitu.capability.data.spi.DataStore;
import cn.youhuale.leitu.core.guard.model.Decision;
import cn.youhuale.leitu.core.guard.model.Retry;
import cn.youhuale.leitu.core.guard.spi.Guard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** 装配：存取器一行声明 + 守卫声明即收编（见 ADR-014）。 */
@Configuration
public class TaskConfig {

    static final JdbcMapping<Task, String> TASK_MAPPING = JdbcMapping.<Task, String>builder()
            .table("tasks")
            .columns("title")
            .idOf(Task::id)
            .values(t -> List.of(t.title()))
            .rowMapper((rs, rowNum) -> new Task(
                    rs.getString("id"), JdbcAudit.fields(rs), rs.getString("title")))
            .build();

    @Bean
    DataStore<Task, String> tasks(JdbcDataStoreFactory factory) {
        return factory.create(TASK_MAPPING);
    }

    /** 演示守卫：task.create 仅 boss 放行（action 与 operationId 点分同源）。 */
    @Bean
    Guard taskCreateGuard() {
        return request -> "task.create".equals(request.action()) && !"boss".equals(request.subject())
                ? Decision.deny("仅 boss 可创建任务（探针守卫）", Retry.never())
                : Decision.allow();
    }
}
