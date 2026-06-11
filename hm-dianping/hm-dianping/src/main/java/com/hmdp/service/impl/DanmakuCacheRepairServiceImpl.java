package com.hmdp.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.DanmakuKafkaProperties;
import com.hmdp.entity.VideoDanmaku;
import com.hmdp.service.DanmakuCacheRepairService;
import com.hmdp.service.IVideoDanmakuService;
import com.hmdp.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DanmakuCacheRepairServiceImpl implements DanmakuCacheRepairService {

    private final IVideoDanmakuService videoDanmakuService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DanmakuKafkaProperties danmakuProperties;

    @Override
    public void refreshVideoDanmakuCache(Long blogId) {
        if (blogId == null) {
            return;
        }
        try {
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
                        objectMapper.writeValueAsString(cacheView(danmaku)),
                        danmaku.getVideoSecond() == null ? 0D : danmaku.getVideoSecond().doubleValue()
                );
            }
            stringRedisTemplate.expire(key, danmakuProperties.getCacheTtlHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            throw new IllegalStateException("Refresh danmaku cache failed, blogId=" + blogId, e);
        }
    }

    private Map<String, Object> cacheView(VideoDanmaku danmaku) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", danmaku.getId());
        view.put("blogId", danmaku.getBlogId());
        view.put("videoId", danmaku.getBlogId());
        view.put("content", danmaku.getContent());
        view.put("videoSecond", danmaku.getVideoSecond() == null ? 0 : danmaku.getVideoSecond());
        view.put("lane", danmaku.getLane() == null ? 0 : danmaku.getLane());
        view.put("createTime", danmaku.getCreateTime());
        return view;
    }
}
