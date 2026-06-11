package com.xhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhs.annotation.RequireRole;
import com.xhs.dto.Result;
import com.xhs.entity.CompensationEvent;
import com.xhs.enums.UserRole;
import com.xhs.service.CompensationEventService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/compensation")
@RequireRole(UserRole.ADMIN)
public class CompensationAdminController {

    @Resource
    private CompensationEventService compensationEventService;

    @GetMapping("/events")
    public Result events(@RequestParam(value = "status", required = false) String status,
                         @RequestParam(value = "eventType", required = false) String eventType,
                         @RequestParam(value = "current", defaultValue = "1") Integer current,
                         @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Page<CompensationEvent> page = compensationEventService.pageEvents(status, eventType, current, size);
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @PostMapping("/events/{id}/replay")
    public Result replay(@PathVariable("id") Long id) {
        boolean success = compensationEventService.replayNow(id);
        return Result.ok(Map.of("id", id, "success", success));
    }

    @PostMapping("/events/{id}/cancel")
    public Result cancel(@PathVariable("id") Long id) {
        CompensationEvent event = compensationEventService.getById(id);
        if (event != null) {
            compensationEventService.cancelByKey(event.getEventKey(), "Canceled by admin");
        }
        return Result.ok(Map.of("id", id, "success", event != null));
    }

    @PostMapping("/replay-due")
    public Result replayDue(@RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        int count = compensationEventService.replayDue(limit);
        return Result.ok(Map.of("count", count));
    }
}
