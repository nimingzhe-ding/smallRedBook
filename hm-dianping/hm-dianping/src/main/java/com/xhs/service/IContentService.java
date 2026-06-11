package com.xhs.service;

import com.xhs.dto.ContentAiRequest;
import com.xhs.dto.ContentProfileUpdateRequest;
import com.xhs.dto.Result;

/**
 * 内容社区聚合服务。
 * 把笔记、作者、关注、点赞、收藏、行为趋势等分散能力包装成前端友好的统一 API。
 */
public interface IContentService {

    /**
     * 查询首页内容流。
     *
     * @param channel 频道：hot/follow/nearby/recommend/video/mall
     * @param query 搜索词，可为空
     * @param current 页码
     * @param x 经度（附近流可选）
     * @param y 纬度（附近流可选）
     * @return 内容流卡片列表
     */
    Result feed(String channel, String query, Integer current, Double x, Double y);

    /**
     * 统一搜索笔记、视频、商品、商家和话题。
     */
    Result search(String query, Integer current);

    /**
     * 查询单篇笔记详情。
     */
    Result detail(Long blogId);

    /**
     * 查询当前登录用户发布的笔记。
     */
    Result mine(Integer current);

    /**
     * 查询当前登录用户收藏的笔记。
     */
    Result collections(Integer current);

    /**
     * 查询当前登录用户点赞过的笔记。
     */
    Result liked(Integer current);

    /**
     * 查询指定用户主页的笔记流。
     */
    Result userNotes(Long userId, Integer current);

    /**
     * 查询指定用户收藏的笔记。
     */
    Result userCollections(Long userId, Integer current);

    /**
     * 查询指定用户点赞过的笔记。
     */
    Result userLiked(Long userId, Integer current);

    /**
     * 查询指定用户关注的人。
     */
    Result following(Long userId, Integer current);

    /**
     * 查询指定用户的粉丝。
     */
    Result followers(Long userId, Integer current);

    /**
     * 查询用户内容主页统计。
     */
    Result profile(Long userId);

    /**
     * 更新当前登录用户主页资料。
     */
    Result updateProfile(ContentProfileUpdateRequest request);

    /**
     * 查询搜索和内容趋势词。
     */
    Result trends();

    /**
     * 搜索联想：前缀匹配话题、商品、笔记标题。
     */
    Result suggestions(String prefix);

    /**
     * 查询当前用户的搜索历史。
     */
    Result searchHistory();

    /**
     * 删除当前用户的一条搜索历史。
     */
    Result deleteSearchHistory(String keyword);

    /**
     * 清空当前用户的搜索历史。
     */
    Result clearSearchHistory();

    /**
     * 查询热搜榜。
     */
    Result hotSearch();

    /**
     * 生成内容搜索场景下的智能推荐。
     */
    Result aiRecommend(ContentAiRequest request);

    /**
     * 生成笔记详情场景下的智能看点总结。
     */
    Result aiNoteSummary(ContentAiRequest request);
}
