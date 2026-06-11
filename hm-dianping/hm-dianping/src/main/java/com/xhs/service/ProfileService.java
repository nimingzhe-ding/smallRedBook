package com.xhs.service;

import com.xhs.dto.ContentProfileUpdateRequest;
import com.xhs.dto.Result;

/**
 * 个人主页服务。
 */
public interface ProfileService {
    Result profile(Long userId);

    Result updateProfile(ContentProfileUpdateRequest request);

    Result following(Long userId, Integer current);

    Result followers(Long userId, Integer current);
}
