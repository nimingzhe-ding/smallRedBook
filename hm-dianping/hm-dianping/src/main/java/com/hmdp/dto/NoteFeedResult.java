package com.hmdp.dto;

import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 笔记流统一返回结构。
 * 兼容 ContentFeedResult 的 JSON 字段，后续可逐步替换旧命名。
 */
@NoArgsConstructor
public class NoteFeedResult extends ContentFeedResult {
    public NoteFeedResult(List<ContentNoteDTO> list, Long total, Boolean hasMore, String query) {
        super(list, total, hasMore, query);
    }
}
