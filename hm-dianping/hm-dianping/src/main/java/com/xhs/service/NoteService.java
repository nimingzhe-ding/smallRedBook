package com.xhs.service;

import com.xhs.dto.Result;
import com.xhs.entity.Blog;

/**
 * 笔记业务服务。
 * 内部暂时沿用 Blog 实体，外部控制器优先依赖 NoteService 命名。
 */
public interface NoteService {
    Result feed(String channel, String query, Integer current, Double x, Double y);

    Result search(String query, Integer current);

    Result detail(Long noteId);

    Result mine(Integer current);

    Result collections(Integer current);

    Result liked(Integer current);

    Result userNotes(Long userId, Integer current);

    Result userCollections(Long userId, Integer current);

    Result userLiked(Long userId, Integer current);

    Result publish(Blog note);

    Result updateOwnNote(Long noteId, Blog note);

    Result deleteOwnNote(Long noteId);

    Result reportNote(Long noteId);

    Blog getNoteEntity(Long noteId);

    boolean increaseCommentCount(Long noteId);

    boolean decreaseCommentCount(Long noteId, long count);
}
