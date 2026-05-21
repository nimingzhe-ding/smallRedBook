package com.hmdp.service;

import com.hmdp.dto.ContentProfileUpdateRequest;
import com.hmdp.dto.Result;

/**
 * 个人主页服务。
 */
public interface ProfileService {
    Result profile(Long userId);

    Result updateProfile(ContentProfileUpdateRequest request);

    Result following(Long userId, Integer current);

    Result followers(Long userId, Integer current);
}
