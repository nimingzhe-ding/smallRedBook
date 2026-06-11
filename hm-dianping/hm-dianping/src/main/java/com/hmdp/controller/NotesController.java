package com.hmdp.controller;

import com.hmdp.annotation.RequireRole;
import com.hmdp.annotation.Idempotent;
import com.hmdp.annotation.SlidingWindowRateLimit;
import com.hmdp.dto.ContentAiRequest;
import com.hmdp.dto.NoteCreateRequest;
import com.hmdp.dto.NoteUpdateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.enums.RateLimitScope;
import com.hmdp.enums.UserRole;
import com.hmdp.service.CollectService;
import com.hmdp.service.LikeService;
import com.hmdp.service.NoteService;
import com.hmdp.service.ProfileService;
import com.hmdp.service.RecommendationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 笔记统一入口控制器 —— 前端主要 API。
 * 所有笔记相关入口统一走笔记社区命名的 service 门面。
 * 内部暂时继续复用 Blog 实体和旧表结构，避免一次性大重命名影响稳定性。
 */
@RestController
@RequestMapping("/notes")
public class NotesController {

    @Resource
    private NoteService noteService;

    @Resource
    private LikeService likeService;

    @Resource
    private CollectService collectService;

    @Resource
    private RecommendationService recommendationService;

    @Resource
    private ProfileService profileService;

    // ======================== 信息流与搜索 ========================

    /**
     * 首页信息流：支持 recommend（推荐）/ hot（热门）/ follow（关注）/ nearby（附近）/ video（视频）/ mall（好物）频道，
     * 可选关键词 query 过滤，支持地理位置参数 (x, y) 做附近推荐。
     */
    @GetMapping("/feed")
    @SlidingWindowRateLimit(key = "notes:feed", maxRequests = 120, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result feed(
            @RequestParam(value = "channel", defaultValue = "recommend") String channel,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y) {
        return noteService.feed(channel, query, current, x, y);
    }

    /**
     * 统一搜索：按关键词全文检索笔记、视频、商品、店铺、话题。
     */
    @GetMapping("/search")
    @SlidingWindowRateLimit(key = "notes:search", maxRequests = 60, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result search(
            @RequestParam("query") String query,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.search(query, current);
    }

    // ======================== 笔记 CRUD ========================

    /**
     * 笔记详情：返回笔记正文、图片/视频、作者信息、关联商品与店铺。
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long noteId) {
        return noteService.detail(noteId);
    }

    /**
     * 发布笔记，需要登录。
     */
    @PostMapping
    @RequireRole(UserRole.USER)
    @SlidingWindowRateLimit(key = "notes:publish", maxRequests = 10, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "notes:publish", expireSeconds = 15)
    public Result publish(@RequestBody NoteCreateRequest request) {
        return noteService.publish(toBlog(request));
    }

    /**
     * 编辑自己的笔记。
     */
    @PutMapping("/{id}")
    @Idempotent(key = "notes:update", expireSeconds = 10)
    public Result update(@PathVariable("id") Long noteId, @RequestBody NoteUpdateRequest request) {
        return noteService.updateOwnNote(noteId, toBlog(request));
    }

    /**
     * 删除自己的笔记。
     */
    @DeleteMapping("/{id}")
    @Idempotent(key = "notes:delete", expireSeconds = 10)
    public Result delete(@PathVariable("id") Long noteId) {
        return noteService.deleteOwnNote(noteId);
    }

    @PutMapping("/{id}/report")
    @SlidingWindowRateLimit(key = "notes:report", maxRequests = 20, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "notes:report", expireSeconds = 30)
    public Result report(@PathVariable("id") Long noteId) {
        return noteService.reportNote(noteId);
    }

    // ======================== 点赞 ========================

    /**
     * 点赞/取消点赞（同一接口，后端幂等切换）。
     */
    @PutMapping("/{id}/like")
    @SlidingWindowRateLimit(key = "notes:like", maxRequests = 120, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result like(@PathVariable("id") Long noteId) {
        return likeService.likeNote(noteId);
    }

    /**
     * 获取点赞用户列表（按时间倒序，前 5 条）。
     */
    @GetMapping("/{id}/likes")
    public Result likes(@PathVariable("id") Long noteId) {
        return likeService.queryNoteLikes(noteId);
    }

    // ======================== 收藏 ========================

    /**
     * 收藏/取消收藏：collect=true 收藏，collect=false 取消。
     */
    @PutMapping("/{id}/collect/{collect}")
    @SlidingWindowRateLimit(key = "notes:collect", maxRequests = 120, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result collect(@PathVariable("id") Long noteId, @PathVariable("collect") Boolean collect) {
        return collectService.collectBlog(noteId, collect);
    }

    /**
     * 查询当前用户是否已收藏该笔记。
     */
    @GetMapping("/{id}/collect")
    public Result isCollected(@PathVariable("id") Long noteId) {
        return collectService.isCollected(noteId);
    }

    // ======================== 个人中心 ========================

    /**
     * 我的笔记列表。
     */
    @GetMapping("/mine")
    public Result mine(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.mine(current);
    }

    /**
     * 我的收藏列表。
     */
    @GetMapping("/collections")
    public Result collections(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.collections(current);
    }

    /**
     * 我点赞过的笔记列表。
     */
    @GetMapping("/liked")
    public Result liked(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.liked(current);
    }

    // ======================== 用户主页数据 ========================

    /**
     * 查看某用户的笔记列表。
     */
    @GetMapping("/user/{id}")
    public Result userNotes(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.userNotes(userId, current);
    }

    /**
     * 查看某用户的收藏列表。
     */
    @GetMapping("/user/{id}/collections")
    public Result userCollections(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.userCollections(userId, current);
    }

    /**
     * 查看某用户点赞过的笔记。
     */
    @GetMapping("/user/{id}/liked")
    public Result userLiked(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return noteService.userLiked(userId, current);
    }

    /**
     * 查看某用户关注的人列表。
     */
    @GetMapping("/user/{id}/following")
    public Result following(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return profileService.following(userId, current);
    }

    /**
     * 查看某用户的粉丝列表。
     */
    @GetMapping("/user/{id}/followers")
    public Result followers(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return profileService.followers(userId, current);
    }

    // ======================== 搜索辅助 ========================

    /**
     * 搜索趋势：返回当前热门搜索词。
     */
    @GetMapping("/trends")
    public Result trends() {
        return recommendationService.trends();
    }

    /**
     * 搜索联想：根据输入前缀返回补全建议。
     */
    @GetMapping("/suggestions")
    @SlidingWindowRateLimit(key = "notes:suggestions", maxRequests = 120, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result suggestions(@RequestParam("prefix") String prefix) {
        return recommendationService.suggestions(prefix);
    }

    /**
     * 获取当前用户的搜索历史。
     */
    @GetMapping("/search-history")
    public Result searchHistory() {
        return recommendationService.searchHistory();
    }

    /**
     * 删除单条搜索历史。
     */
    @DeleteMapping("/search-history")
    public Result deleteSearchHistory(@RequestParam("keyword") String keyword) {
        return recommendationService.deleteSearchHistory(keyword);
    }

    /**
     * 清空全部搜索历史。
     */
    @DeleteMapping("/search-history/all")
    public Result clearSearchHistory() {
        return recommendationService.clearSearchHistory();
    }

    /**
     * 热门搜索词排行。
     */
    @GetMapping("/hot-search")
    public Result hotSearch() {
        return recommendationService.hotSearch();
    }

    // ======================== AI 智能体 ========================

    /**
     * AI 个性化推荐：基于用户行为和偏好生成推荐理由与结果。
     */
    @PostMapping("/ai/recommend")
    @SlidingWindowRateLimit(key = "notes:ai:recommend", maxRequests = 10, windowSeconds = 60, scope = RateLimitScope.USER_OR_IP)
    public Result aiRecommend(@RequestBody ContentAiRequest request) {
        return recommendationService.aiRecommend(request);
    }

    /**
     * AI 笔记总结：调用大模型对笔记内容生成摘要和看点。
     */
    @PostMapping("/ai/summary")
    public Result aiNoteSummary(@RequestBody ContentAiRequest request) {
        return recommendationService.aiNoteSummary(request);
    }

    private Blog toBlog(NoteCreateRequest request) {
        NoteCreateRequest source = request == null ? new NoteCreateRequest() : request;
        Blog note = new Blog();
        note.setShopId(source.getShopId());
        note.setTitle(source.getTitle());
        note.setImages(source.getImages());
        note.setVideoUrl(source.getVideoUrl());
        note.setContentType(source.getContentType());
        note.setTags(source.getTags());
        note.setContent(source.getContent());
        note.setProductIds(source.getProductIds());
        return note;
    }

    private Blog toBlog(NoteUpdateRequest request) {
        NoteUpdateRequest source = request == null ? new NoteUpdateRequest() : request;
        Blog note = new Blog();
        note.setShopId(source.getShopId());
        note.setTitle(source.getTitle());
        note.setImages(source.getImages());
        note.setVideoUrl(source.getVideoUrl());
        note.setContentType(source.getContentType());
        note.setTags(source.getTags());
        note.setContent(source.getContent());
        note.setProductIds(source.getProductIds());
        return note;
    }
}
