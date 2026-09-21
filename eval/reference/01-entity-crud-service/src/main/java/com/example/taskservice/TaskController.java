package com.example.taskservice;

import cn.youhuale.leitu.adapter.web.FailureNoticeException;
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
@RequestMapping("/tasks")
public class TaskController {

    private final DataStore<Task, String> tasks;
    private final GuardChain chain;
    private final ExecutionContextReader who;

    public TaskController(DataStore<Task, String> tasks, GuardChain chain, ExecutionContextReader who) {
        this.tasks = tasks;
        this.chain = chain;
        this.who = who;
    }

    @Operation(operationId = "task.create", summary = "创建任务")
    @PostMapping
    public Task create(@RequestParam String id, @RequestParam String title) {
        Decision decision = chain.check(AccessRequest.inbound(
                who.current().operator().subject(), "task.create", "-"));
        if (decision.isDeny()) {
            throw FailureNoticeException.from(decision, who);
        }
        return tasks.save(Task.create(id, title));
    }

    @Operation(operationId = "task.get", summary = "读取任务")
    @GetMapping("/{id}")
    public Task get(@PathVariable String id) {
        return tasks.findById(id).orElseThrow(() -> new FailureNoticeException(FailureNotice.business(
                "task.not-found", "任务不存在",
                "任务 " + id + " 不存在或已删除；请确认编号后重试", who.current())));
    }
}
