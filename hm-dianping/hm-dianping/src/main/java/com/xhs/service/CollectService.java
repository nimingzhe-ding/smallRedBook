package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.Result;
import com.xhs.entity.BlogCollect;

/**
 * 笔记收藏服务。
 */
public interface CollectService extends IService<BlogCollect> {
    Result collectBlog(Long noteId, Boolean collect);

    Result isCollected(Long noteId);
}
