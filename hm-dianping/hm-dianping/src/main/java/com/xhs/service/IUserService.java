package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.LoginFormDTO;
import com.xhs.dto.Result;
import com.xhs.entity.User;

import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 登出功能
     * @param token
     * @return
     */
    Result logout(String token);


    /**
     * 签到
     * @return
     */
    Result sign();

    /**
     * 签到统计
     * @return
     */
    Result signCount();
}
