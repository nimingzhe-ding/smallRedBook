package com.xhs.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 内容流统一返回结构。
 * hasMore 方便前端判断是否继续触底加载，query 用于回显当前搜索词。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentFeedResult {
    private List<ContentNoteDTO> list;
    private Long total;
    private Boolean hasMore;
    private String query;
}
