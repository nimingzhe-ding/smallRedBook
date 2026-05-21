package com.hmdp.service;

import com.hmdp.dto.Result;

/**
 * 笔记收藏业务接口。
 */
public interface IBlogCollectService extends CollectService {

    /**
     * 收藏或取消收藏笔记。
     */
    Result collectBlog(Long blogId, Boolean collect);

    /**
     * 查询当前用户是否收藏了某篇笔记。
     */
    Result isCollected(Long blogId);
}
