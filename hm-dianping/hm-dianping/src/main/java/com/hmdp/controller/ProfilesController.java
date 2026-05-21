package com.hmdp.controller;

import com.hmdp.dto.ContentProfileUpdateRequest;
import com.hmdp.dto.Result;
import com.hmdp.service.ProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 笔记社区个人主页入口。
 */
@RestController
@RequestMapping("/profiles")
public class ProfilesController {

    @Resource
    private ProfileService profileService;

    @GetMapping("/me")
    public Result me() {
        return profileService.profile(null);
    }

    @GetMapping("/{id}")
    public Result profile(@PathVariable("id") Long userId) {
        return profileService.profile(userId);
    }

    @PutMapping("/me")
    public Result updateMe(@RequestBody ContentProfileUpdateRequest request) {
        return profileService.updateProfile(request);
    }
}
