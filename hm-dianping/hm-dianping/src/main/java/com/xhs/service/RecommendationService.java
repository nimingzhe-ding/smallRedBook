package com.xhs.service;

import com.xhs.dto.ContentAiRequest;
import com.xhs.dto.Result;

/**
 * 笔记推荐、搜索趋势和智能总结服务。
 */
public interface RecommendationService {
    Result trends();

    Result suggestions(String prefix);

    Result searchHistory();

    Result deleteSearchHistory(String keyword);

    Result clearSearchHistory();

    Result hotSearch();

    Result aiRecommend(ContentAiRequest request);

    Result aiNoteSummary(ContentAiRequest request);
}
