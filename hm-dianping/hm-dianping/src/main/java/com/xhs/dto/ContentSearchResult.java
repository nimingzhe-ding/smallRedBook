package com.xhs.dto;

import com.xhs.entity.MallProduct;
import com.xhs.entity.Shop;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 统一搜索结果。
 * 一次返回笔记、视频、商品、商家和话题，前端只负责按 Tab 展示。
 */
@Data
public class ContentSearchResult {
    private String query;
    private String summary;
    private List<ContentNoteDTO> notes;
    private List<ContentNoteDTO> videos;
    private List<MallProduct> products;
    private Map<Long, List<ContentNoteDTO>> productNotes;
    private List<Shop> shops;
    private List<ContentTrendDTO> topics;
    private List<String> relatedQueries;
}
