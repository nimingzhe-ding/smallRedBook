package com.xhs.service;

import com.xhs.dto.Result;
import com.xhs.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {
    /**
     * 关注或取关用户
     * @param followUserId
     * @param isFollow
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     *
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 共同关注
     * @param followUserId
     * @return
     */
    Result followCommons(Long followUserId);
}
