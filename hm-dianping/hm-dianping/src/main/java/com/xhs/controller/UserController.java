package com.xhs.controller;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.xhs.annotation.SlidingWindowRateLimit;
import com.xhs.dto.LoginFormDTO;
import com.xhs.dto.Result;
import com.xhs.dto.UserAccountDTO;
import com.xhs.dto.UserDTO;
import com.xhs.entity.User;
import com.xhs.entity.UserInfo;
import com.xhs.enums.RateLimitScope;
import com.xhs.service.IUserInfoService;
import com.xhs.service.IUserService;
import com.xhs.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    @SlidingWindowRateLimit(key = "user:code", maxRequests = 5, windowSeconds = 60, scope = RateLimitScope.IP)
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    @SlidingWindowRateLimit(key = "user:login", maxRequests = 10, windowSeconds = 60, scope = RateLimitScope.IP)
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能

        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        // 实现登出功能
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return Result.ok(); // 没带 token 本来就没登录
        }
        return userService.logout(token);
    }

    @GetMapping("/me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/account")
    public Result account() {
        UserDTO current = UserHolder.getUser();
        if (current == null) {
            return Result.ok();
        }
        User user = userService.getById(current.getId());
        if (user == null) {
            return Result.ok();
        }
        UserAccountDTO account = BeanUtil.copyProperties(user, UserAccountDTO.class);
        account.setMaskedPhone(maskPhone(user.getPhone()));
        return Result.ok(account);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }
    @GetMapping("/sign")
    public Result sign(){
        return userService.sign();
    }
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    private String maskPhone(String phone) {
        if (StrUtil.isBlank(phone) || phone.length() < 7) {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
