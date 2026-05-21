package com.hmdp.dto;

import com.hmdp.entity.MallProduct;
import com.hmdp.entity.Shop;
import lombok.Data;

import java.util.List;

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
    private List<Shop> shops;
    private List<ContentTrendDTO> topics;
    private List<String> relatedQueries;
}
