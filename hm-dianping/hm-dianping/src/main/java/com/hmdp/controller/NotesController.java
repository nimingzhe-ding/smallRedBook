package com.hmdp.controller;

import com.hmdp.annotation.RequireRole;
import com.hmdp.dto.ContentAiRequest;
import com.hmdp.dto.NoteCreateRequest;
import com.hmdp.dto.NoteUpdateRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.enums.UserRole;
import com.hmdp.service.IBlogCollectService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IContentService;
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
 * 所有笔记相关的读（feed/搜索/详情/个人主页）走 IContentService，
 * 写（发布/编辑/删除/点赞）走 IBlogService，收藏走 IBlogCollectService。
 */
@RestController
@RequestMapping("/notes")
public class NotesController {

    @Resource
    private IContentService contentService;

    @Resource
    private IBlogService blogService;

    @Resource
    private IBlogCollectService blogCollectService;

    // ======================== 信息流与搜索 ========================

    /**
     * 首页信息流：支持 hot（热门）/ follow（关注）/ nearby（附近）三个频道，
     * 可选关键词 query 过滤，支持地理位置参数 (x, y) 做附近推荐。
     */
    @GetMapping("/feed")
    public Result feed(
            @RequestParam(value = "channel", defaultValue = "hot") String channel,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y) {
        return contentService.feed(channel, query, current, x, y);
    }

    /**
     * 统一搜索：按关键词全文检索笔记、视频、商品、店铺、话题。
     */
    @GetMapping("/search")
    public Result search(
            @RequestParam("query") String query,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.search(query, current);
    }

    // ======================== 笔记 CRUD ========================

    /**
     * 笔记详情：返回笔记正文、图片/视频、作者信息、关联商品与店铺。
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable("id") Long noteId) {
        return contentService.detail(noteId);
    }

    /**
     * 发布笔记，需要登录。
     */
    @PostMapping
    @RequireRole(UserRole.USER)
    public Result publish(@RequestBody NoteCreateRequest request) {
        return blogService.saveBlog(toBlog(request));
    }

    /**
     * 编辑自己的笔记。
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long noteId, @RequestBody NoteUpdateRequest request) {
        return blogService.updateOwnBlog(noteId, toBlog(request));
    }

    /**
     * 删除自己的笔记。
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable("id") Long noteId) {
        return blogService.deleteOwnBlog(noteId);
    }

    // ======================== 点赞 ========================

    /**
     * 点赞/取消点赞（同一接口，后端幂等切换）。
     */
    @PutMapping("/{id}/like")
    public Result like(@PathVariable("id") Long noteId) {
        return blogService.likeBlog(noteId);
    }

    /**
     * 获取点赞用户列表（按时间倒序，前 5 条）。
     */
    @GetMapping("/{id}/likes")
    public Result likes(@PathVariable("id") Long noteId) {
        return blogService.queryBlogLikes(noteId);
    }

    // ======================== 收藏 ========================

    /**
     * 收藏/取消收藏：collect=true 收藏，collect=false 取消。
     */
    @PutMapping("/{id}/collect/{collect}")
    public Result collect(@PathVariable("id") Long noteId, @PathVariable("collect") Boolean collect) {
        return blogCollectService.collectBlog(noteId, collect);
    }

    /**
     * 查询当前用户是否已收藏该笔记。
     */
    @GetMapping("/{id}/collect")
    public Result isCollected(@PathVariable("id") Long noteId) {
        return blogCollectService.isCollected(noteId);
    }

    // ======================== 个人中心 ========================

    /**
     * 我的笔记列表。
     */
    @GetMapping("/mine")
    public Result mine(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.mine(current);
    }

    /**
     * 我的收藏列表。
     */
    @GetMapping("/collections")
    public Result collections(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.collections(current);
    }

    /**
     * 我点赞过的笔记列表。
     */
    @GetMapping("/liked")
    public Result liked(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.liked(current);
    }

    // ======================== 用户主页数据 ========================

    /**
     * 查看某用户的笔记列表。
     */
    @GetMapping("/user/{id}")
    public Result userNotes(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.userNotes(userId, current);
    }

    /**
     * 查看某用户的收藏列表。
     */
    @GetMapping("/user/{id}/collections")
    public Result userCollections(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.userCollections(userId, current);
    }

    /**
     * 查看某用户点赞过的笔记。
     */
    @GetMapping("/user/{id}/liked")
    public Result userLiked(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.userLiked(userId, current);
    }

    /**
     * 查看某用户关注的人列表。
     */
    @GetMapping("/user/{id}/following")
    public Result following(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.following(userId, current);
    }

    /**
     * 查看某用户的粉丝列表。
     */
    @GetMapping("/user/{id}/followers")
    public Result followers(
            @PathVariable("id") Long userId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return contentService.followers(userId, current);
    }

    // ======================== 搜索辅助 ========================

    /**
     * 搜索趋势：返回当前热门搜索词。
     */
    @GetMapping("/trends")
    public Result trends() {
        return contentService.trends();
    }

    /**
     * 搜索联想：根据输入前缀返回补全建议。
     */
    @GetMapping("/suggestions")
    public Result suggestions(@RequestParam("prefix") String prefix) {
        return contentService.suggestions(prefix);
    }

    /**
     * 获取当前用户的搜索历史。
     */
    @GetMapping("/search-history")
    public Result searchHistory() {
        return contentService.searchHistory();
    }

    /**
     * 删除单条搜索历史。
     */
    @DeleteMapping("/search-history")
    public Result deleteSearchHistory(@RequestParam("keyword") String keyword) {
        return contentService.deleteSearchHistory(keyword);
    }

    /**
     * 清空全部搜索历史。
     */
    @DeleteMapping("/search-history/all")
    public Result clearSearchHistory() {
        return contentService.clearSearchHistory();
    }

    /**
     * 热门搜索词排行。
     */
    @GetMapping("/hot-search")
    public Result hotSearch() {
        return contentService.hotSearch();
    }

    // ======================== AI 智能体 ========================

    /**
     * AI 个性化推荐：基于用户行为和偏好生成推荐理由与结果。
     */
    @PostMapping("/ai/recommend")
    public Result aiRecommend(@RequestBody ContentAiRequest request) {
        return contentService.aiRecommend(request);
    }

    /**
     * AI 笔记总结：调用大模型对笔记内容生成摘要和看点。
     */
    @PostMapping("/ai/summary")
    public Result aiNoteSummary(@RequestBody ContentAiRequest request) {
        return contentService.aiNoteSummary(request);
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
