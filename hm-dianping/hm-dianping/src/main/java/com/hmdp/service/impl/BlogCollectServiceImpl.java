package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogCollect;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.BlogCollectMapper;
import com.hmdp.enums.EventType;
import com.hmdp.service.IBlogCollectService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.INoteEventService;
import com.hmdp.service.IUserNotificationService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 笔记收藏业务实现。
 * 收藏数据落库，前端可用它恢复收藏状态，后续也方便扩展收藏列表。
 */
@Service
public class BlogCollectServiceImpl extends ServiceImpl<BlogCollectMapper, BlogCollect> implements IBlogCollectService {
    @Resource
    private IBlogService blogService;

    @Resource
    private IUserNotificationService notificationService;
    @Resource
    private INoteEventService noteEventService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result collectBlog(Long blogId, Boolean collect) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (blogId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "笔记ID不能为空");
        }
        Long userId = user.getId();
        if (Boolean.TRUE.equals(collect)) {
            long count = query().eq("user_id", userId).eq("blog_id", blogId).count();
            if (count == 0) {
                BlogCollect blogCollect = new BlogCollect();
                blogCollect.setUserId(userId);
                blogCollect.setBlogId(blogId);
                save(blogCollect);
                Blog blog = blogService.getById(blogId);
                if (blog != null) {
                    notificationService.notifyUser(blog.getUserId(), userId, "COLLECT", "有人收藏了你的笔记",
                            "你的笔记《" + (blog.getTitle() == null ? "未命名笔记" : blog.getTitle()) + "》被收藏了。", blogId, null);
                }
                noteEventService.track(userId, blogId, EventType.COLLECT, null, null);
                // 更新用户兴趣画像
                if (blog != null && blog.getTags() != null) {
                    for (String tag : blog.getTags().split(",")) {
                        String trimmed = tag.trim();
                        if (!trimmed.isEmpty()) {
                            stringRedisTemplate.opsForZSet().incrementScore(
                                    RedisConstants.USER_INTEREST_KEY + userId, trimmed, 4);
                        }
                    }
                    stringRedisTemplate.expire(RedisConstants.USER_INTEREST_KEY + userId,
                            RedisConstants.USER_INTEREST_TTL, java.util.concurrent.TimeUnit.DAYS);
                }
            }
        } else {
            boolean removed = remove(new QueryWrapper<BlogCollect>()
                    .eq("user_id", userId)
                    .eq("blog_id", blogId));
            if (removed) {
                noteEventService.track(userId, blogId, EventType.UNCOLLECT, null, null);
            }
        }
        return Result.ok();
    }

    @Override
    public Result isCollected(Long blogId) {
        UserDTO user = UserHolder.getUser();
        if (user == null || blogId == null) {
            return Result.ok(false);
        }
        long count = query().eq("user_id", user.getId()).eq("blog_id", blogId).count();
        return Result.ok(count > 0);
    }
}
