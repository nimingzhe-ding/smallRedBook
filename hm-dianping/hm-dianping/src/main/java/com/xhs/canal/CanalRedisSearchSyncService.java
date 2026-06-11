package com.xhs.canal;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.compensation.CompensationEventTypes;
import com.xhs.config.CanalSyncProperties;
import com.xhs.entity.Blog;
import com.xhs.entity.User;
import com.xhs.entity.VideoDanmaku;
import com.xhs.enums.ContentType;
import com.xhs.service.CompensationEventService;
import com.xhs.service.DanmakuCacheRepairService;
import com.xhs.service.SearchIndexService;
import com.xhs.service.impl.VideoDanmakuServiceImpl;
import com.xhs.utils.RedisConstants;
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
    private final SearchIndexService searchIndexService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CompensationEventService compensationEventService;
    private final DanmakuCacheRepairService danmakuCacheRepairService;

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
            evictVideoWithCompensation(id);
            recordSearchFailure(
                    searchIndexService.deleteBlog(id),
                    CompensationEventTypes.SEARCH_INDEX_BLOG_DELETE,
                    "BLOG",
                    id,
                    Map.of("blogId", id),
                    "delete video index " + id
            );
            return;
        }
        Blog blog = toBlog(change.afterColumns());
        if (isVisibleVideo(blog)) {
            cacheVideoWithCompensation(id, blog);
            recordSearchFailure(
                    searchIndexService.indexBlog(blog),
                    CompensationEventTypes.SEARCH_INDEX_BLOG_UPSERT,
                    "BLOG",
                    id,
                    Map.of("blogId", id),
                    "index video " + id
            );
        } else {
            evictVideoWithCompensation(id);
            recordSearchFailure(
                    searchIndexService.deleteBlog(id),
                    CompensationEventTypes.SEARCH_INDEX_BLOG_DELETE,
                    "BLOG",
                    id,
                    Map.of("blogId", id),
                    "delete video index " + id
            );
        }
    }

    private void syncUser(CanalRowChange change) throws Exception {
        Long id = longValue(change.currentColumns(), "id");
        if (id == null) {
            return;
        }
        if (change.delete()) {
            evictUserWithCompensation(id);
            recordSearchFailure(
                    searchIndexService.deleteUser(id),
                    CompensationEventTypes.SEARCH_INDEX_USER_DELETE,
                    "USER",
                    id,
                    Map.of("userId", id),
                    "delete user index " + id
            );
            return;
        }
        User user = toUser(change.afterColumns());
        cacheUserWithCompensation(id, user);
        recordSearchFailure(
                searchIndexService.indexUser(user),
                CompensationEventTypes.SEARCH_INDEX_USER_UPSERT,
                "USER",
                id,
                Map.of("userId", id),
                "index user " + id
        );
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
            recordSearchFailure(
                    searchIndexService.deleteDanmaku(id),
                    CompensationEventTypes.SEARCH_INDEX_DANMAKU_DELETE,
                    "DANMAKU",
                    id,
                    Map.of("danmakuId", id),
                    "delete danmaku index " + id
            );
        } else {
            VideoDanmaku danmaku = toDanmaku(change.afterColumns());
            if (isVisibleDanmaku(danmaku)) {
                recordSearchFailure(
                        searchIndexService.indexDanmaku(danmaku),
                        CompensationEventTypes.SEARCH_INDEX_DANMAKU_UPSERT,
                        "DANMAKU",
                        id,
                        Map.of("danmakuId", id),
                        "index danmaku " + id
                );
            } else {
                recordSearchFailure(
                        searchIndexService.deleteDanmaku(id),
                        CompensationEventTypes.SEARCH_INDEX_DANMAKU_DELETE,
                        "DANMAKU",
                        id,
                        Map.of("danmakuId", id),
                        "delete danmaku index " + id
                );
            }
        }
        if (blogId != null) {
            refreshBlogIds.add(blogId);
        }
    }

    private void refreshDanmakuCache(Long blogId) {
        try {
            danmakuCacheRepairService.refreshVideoDanmakuCache(blogId);
        } catch (Exception e) {
            compensationEventService.record(
                    CompensationEventTypes.DANMAKU_REDIS_ZSET_REFRESH,
                    "DANMAKU",
                    String.valueOf(blogId),
                    "danmaku:redis-zset:refresh:" + blogId,
                    Map.of("blogId", blogId),
                    e.getMessage()
            );
        }
    }

    private void cache(String key, Object value) throws Exception {
        stringRedisTemplate.opsForValue().set(
                key,
                objectMapper.writeValueAsString(value),
                canalProperties.getCacheTtlMinutes(),
                TimeUnit.MINUTES
        );
    }

    private void cacheVideoWithCompensation(Long id, Blog blog) {
        try {
            cache(RedisConstants.CACHE_VIDEO_KEY + id, blog);
        } catch (Exception e) {
            compensationEventService.record(
                    CompensationEventTypes.REDIS_VIDEO_CACHE_REFRESH,
                    "BLOG",
                    String.valueOf(id),
                    "redis:video:cache:refresh:" + id,
                    Map.of("blogId", id),
                    e.getMessage()
            );
        }
    }

    private void evictVideoWithCompensation(Long id) {
        try {
            stringRedisTemplate.delete(RedisConstants.CACHE_VIDEO_KEY + id);
            stringRedisTemplate.delete(RedisConstants.VIDEO_DANMAKU_ZSET_KEY + id);
        } catch (Exception e) {
            compensationEventService.record(
                    CompensationEventTypes.REDIS_VIDEO_CACHE_EVICT,
                    "BLOG",
                    String.valueOf(id),
                    "redis:video:cache:evict:" + id,
                    Map.of("blogId", id),
                    e.getMessage()
            );
        }
    }

    private void cacheUserWithCompensation(Long id, User user) {
        try {
            cache(RedisConstants.CACHE_USER_KEY + id, userCacheView(user));
        } catch (Exception e) {
            compensationEventService.record(
                    CompensationEventTypes.REDIS_USER_CACHE_REFRESH,
                    "USER",
                    String.valueOf(id),
                    "redis:user:cache:refresh:" + id,
                    Map.of("userId", id),
                    e.getMessage()
            );
        }
    }

    private void evictUserWithCompensation(Long id) {
        try {
            stringRedisTemplate.delete(RedisConstants.CACHE_USER_KEY + id);
        } catch (Exception e) {
            compensationEventService.record(
                    CompensationEventTypes.REDIS_USER_CACHE_EVICT,
                    "USER",
                    String.valueOf(id),
                    "redis:user:cache:evict:" + id,
                    Map.of("userId", id),
                    e.getMessage()
            );
        }
    }

    private void recordSearchFailure(boolean success, String eventType, String bizType, Long bizId,
                                     Map<String, Object> payload, String action) {
        if (success) {
            return;
        }
        compensationEventService.record(
                eventType,
                bizType,
                String.valueOf(bizId),
                "search:" + eventType.toLowerCase(Locale.ROOT) + ":" + bizId,
                payload,
                "Elasticsearch sync failed: " + action
        );
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
