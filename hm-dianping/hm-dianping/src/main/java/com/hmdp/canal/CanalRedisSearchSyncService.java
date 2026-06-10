package com.hmdp.canal;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.CanalSyncProperties;
import com.hmdp.config.DanmakuKafkaProperties;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.entity.VideoDanmaku;
import com.hmdp.enums.ContentType;
import com.hmdp.service.SearchIndexService;
import com.hmdp.service.IVideoDanmakuService;
import com.hmdp.service.impl.VideoDanmakuServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanalRedisSearchSyncService {

    private static final String TABLE_BLOG = "tb_blog";
    private static final String TABLE_USER = "tb_user";
    private static final String TABLE_DANMAKU = "tb_video_danmaku";
    private static final DateTimeFormatter MYSQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CanalSyncProperties canalProperties;
    private final DanmakuKafkaProperties danmakuProperties;
    private final IVideoDanmakuService videoDanmakuService;
    private final SearchIndexService searchIndexService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public boolean supports(String tableName) {
        return TABLE_BLOG.equals(tableName) || TABLE_USER.equals(tableName) || TABLE_DANMAKU.equals(tableName);
    }

    public void sync(List<CanalRowChange> changes) throws Exception {
        Set<Long> danmakuRefreshBlogIds = new LinkedHashSet<>();
        for (CanalRowChange change : changes) {
            switch (change.tableName()) {
                case TABLE_BLOG -> syncVideo(change);
                case TABLE_USER -> syncUser(change);
                case TABLE_DANMAKU -> syncDanmaku(change, danmakuRefreshBlogIds);
                default -> {
                }
            }
        }
        for (Long blogId : danmakuRefreshBlogIds) {
            refreshDanmakuCache(blogId);
        }
    }

    private void syncVideo(CanalRowChange change) throws Exception {
        Long id = longValue(change.currentColumns(), "id");
        if (id == null) {
            return;
        }
        if (change.delete()) {
            evictVideo(id);
            requireSearch(searchIndexService.deleteBlog(id), "delete video index " + id);
            return;
        }
        Blog blog = toBlog(change.afterColumns());
        if (isVisibleVideo(blog)) {
            cache(RedisConstants.CACHE_VIDEO_KEY + id, blog);
            requireSearch(searchIndexService.indexBlog(blog), "index video " + id);
        } else {
            evictVideo(id);
            requireSearch(searchIndexService.deleteBlog(id), "delete video index " + id);
        }
    }

    private void syncUser(CanalRowChange change) throws Exception {
        Long id = longValue(change.currentColumns(), "id");
        if (id == null) {
            return;
        }
        if (change.delete()) {
            stringRedisTemplate.delete(RedisConstants.CACHE_USER_KEY + id);
            requireSearch(searchIndexService.deleteUser(id), "delete user index " + id);
            return;
        }
        User user = toUser(change.afterColumns());
        cache(RedisConstants.CACHE_USER_KEY + id, userCacheView(user));
        requireSearch(searchIndexService.indexUser(user), "index user " + id);
    }

    private void syncDanmaku(CanalRowChange change, Set<Long> refreshBlogIds) {
        Long id = longValue(change.currentColumns(), "id");
        Long blogId = firstNonNull(
                longValue(change.afterColumns(), "blog_id"),
                longValue(change.beforeColumns(), "blog_id")
        );
        if (id == null) {
            return;
        }
        if (change.delete()) {
            requireSearch(searchIndexService.deleteDanmaku(id), "delete danmaku index " + id);
        } else {
            VideoDanmaku danmaku = toDanmaku(change.afterColumns());
            if (isVisibleDanmaku(danmaku)) {
                requireSearch(searchIndexService.indexDanmaku(danmaku), "index danmaku " + id);
            } else {
                requireSearch(searchIndexService.deleteDanmaku(id), "delete danmaku index " + id);
            }
        }
        if (blogId != null) {
            refreshBlogIds.add(blogId);
        }
    }

    private void evictVideo(Long id) {
        stringRedisTemplate.delete(RedisConstants.CACHE_VIDEO_KEY + id);
        stringRedisTemplate.delete(RedisConstants.VIDEO_DANMAKU_ZSET_KEY + id);
    }

    private void refreshDanmakuCache(Long blogId) throws Exception {
        if (blogId == null) {
            return;
        }
        String key = RedisConstants.VIDEO_DANMAKU_ZSET_KEY + blogId;
        stringRedisTemplate.delete(key);
        List<VideoDanmaku> list = videoDanmakuService.query()
                .eq("blog_id", blogId)
                .eq("status", VideoDanmakuServiceImpl.STATUS_NORMAL)
                .orderByAsc("video_second")
                .last("LIMIT " + danmakuProperties.getCacheLimit())
                .list();
        if (list.isEmpty()) {
            return;
        }
        for (VideoDanmaku danmaku : list) {
            stringRedisTemplate.opsForZSet().add(
                    key,
                    objectMapper.writeValueAsString(danmakuCacheView(danmaku)),
                    danmaku.getVideoSecond() == null ? 0D : danmaku.getVideoSecond().doubleValue()
            );
        }
        stringRedisTemplate.expire(key, danmakuProperties.getCacheTtlHours(), TimeUnit.HOURS);
    }

    private void cache(String key, Object value) throws Exception {
        stringRedisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(value),
                canalProperties.getCacheTtlMinutes(),
                TimeUnit.MINUTES
        );
    }

    private void requireSearch(boolean success, String action) {
        if (!success) {
            throw new IllegalStateException("Elasticsearch sync failed: " + action);
        }
    }

    private boolean isVisibleVideo(Blog blog) {
        return blog != null
                && blog.getId() != null
                && statusOf(blog.getStatus()) == 0
                && ContentType.supportsDanmaku(blog.getContentType(), blog.getVideoUrl());
    }

    private boolean isVisibleDanmaku(VideoDanmaku danmaku) {
        return danmaku != null
                && danmaku.getId() != null
                && danmaku.getBlogId() != null
                && statusOf(danmaku.getStatus()) == VideoDanmakuServiceImpl.STATUS_NORMAL
                && StrUtil.isNotBlank(danmaku.getContent());
    }

    private Blog toBlog(Map<String, String> row) {
        Blog blog = new Blog()
                .setId(longValue(row, "id"))
                .setShopId(longValue(row, "shop_id"))
                .setUserId(longValue(row, "user_id"))
                .setTitle(text(row, "title"))
                .setImages(text(row, "images"))
                .setVideoUrl(text(row, "video_url"))
                .setTags(text(row, "tags"))
                .setContent(text(row, "content"))
                .setLiked(intValue(row, "liked"))
                .setComments(intValue(row, "comments"))
                .setStatus(intValue(row, "status"))
                .setReportReason(text(row, "report_reason"))
                .setReportCount(intValue(row, "report_count"))
                .setReporterId(longValue(row, "reporter_id"))
                .setAuditRemark(text(row, "audit_remark"))
                .setAuditorId(longValue(row, "auditor_id"))
                .setAuditTime(dateTimeValue(row, "audit_time"))
                .setCreateTime(dateTimeValue(row, "create_time"))
                .setUpdateTime(dateTimeValue(row, "update_time"));
        blog.setContentType(text(row, "content_type"));
        return blog;
    }

    private User toUser(Map<String, String> row) {
        return new User()
                .setId(longValue(row, "id"))
                .setPhone(text(row, "phone"))
                .setNickName(text(row, "nick_name"))
                .setIcon(StrUtil.blankToDefault(text(row, "icon"), ""))
                .setRole(firstNonNull(intValue(row, "role"), 1))
                .setCreateTime(dateTimeValue(row, "create_time"))
                .setUpdateTime(dateTimeValue(row, "update_time"));
    }

    private VideoDanmaku toDanmaku(Map<String, String> row) {
        return new VideoDanmaku()
                .setId(longValue(row, "id"))
                .setBlogId(longValue(row, "blog_id"))
                .setUserId(longValue(row, "user_id"))
                .setContent(text(row, "content"))
                .setVideoSecond(intValue(row, "video_second"))
                .setLane(intValue(row, "lane"))
                .setStatus(intValue(row, "status"))
                .setReportReason(text(row, "report_reason"))
                .setReportCount(intValue(row, "report_count"))
                .setReporterId(longValue(row, "reporter_id"))
                .setAuditRemark(text(row, "audit_remark"))
                .setAuditorId(longValue(row, "auditor_id"))
                .setAuditTime(dateTimeValue(row, "audit_time"))
                .setCreateTime(dateTimeValue(row, "create_time"))
                .setUpdateTime(dateTimeValue(row, "update_time"));
    }

    private Map<String, Object> userCacheView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("phone", user.getPhone());
        view.put("nickName", user.getNickName());
        view.put("icon", user.getIcon());
        view.put("role", user.getRole());
        view.put("createTime", user.getCreateTime());
        view.put("updateTime", user.getUpdateTime());
        return view;
    }

    private Map<String, Object> danmakuCacheView(VideoDanmaku danmaku) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", danmaku.getId());
        view.put("blogId", danmaku.getBlogId());
        view.put("videoId", danmaku.getBlogId());
        view.put("content", danmaku.getContent());
        view.put("videoSecond", firstNonNull(danmaku.getVideoSecond(), 0));
        view.put("lane", firstNonNull(danmaku.getLane(), 0));
        view.put("createTime", danmaku.getCreateTime());
        return view;
    }

    private String text(Map<String, String> row, String name) {
        return row == null ? null : row.get(name.toLowerCase(Locale.ROOT));
    }

    private Long longValue(Map<String, String> row, String name) {
        String value = text(row, name);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Map<String, String> row, String name) {
        String value = text(row, name);
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime dateTimeValue(Map<String, String> row, String name) {
        String value = text(row, name);
        if (StrUtil.isBlank(value) || value.startsWith("0000-00-00")) {
            return null;
        }
        String normalized = value.length() > 19 ? value.substring(0, 19) : value;
        try {
            return normalized.contains("T")
                    ? LocalDateTime.parse(normalized)
                    : LocalDateTime.parse(normalized, MYSQL_DATE_TIME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private int statusOf(Integer status) {
        return status == null ? 0 : status;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
