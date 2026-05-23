package com.hmdp.controller;

import com.hmdp.annotation.RequireRole;
import com.hmdp.dto.Result;
import com.hmdp.enums.UserRole;
import com.hmdp.service.SearchIndexService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/search")
@RequireRole(UserRole.ADMIN)
public class SearchAdminController {

    @Resource
    private SearchIndexService searchIndexService;

    @GetMapping("/status")
    public Result status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", searchIndexService.isEnabled());
        data.put("available", searchIndexService.isAvailable());
        return Result.ok(data);
    }

    @PostMapping("/rebuild")
    public Result rebuild() {
        return Result.ok(searchIndexService.rebuildAll());
    }
}
