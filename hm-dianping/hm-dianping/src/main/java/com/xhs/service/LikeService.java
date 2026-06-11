package com.xhs.service;

import com.xhs.dto.Result;

/**
 * 笔记点赞服务。
 */
public interface LikeService {
    Result likeNote(Long noteId);

    Result queryNoteLikes(Long noteId);
}
