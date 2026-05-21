package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogLike;
import com.hmdp.entity.BlogProduct;
import com.hmdp.entity.BlogCollect;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.ContentTopic;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.entity.Follow;
import com.hmdp.entity.MallProduct;
import com.hmdp.entity.User;
import com.hmdp.enums.ContentType;
import com.hmdp.enums.EventType;
import com.hmdp.mapper.BlogLikeMapper;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.BlogProductMapper;
import com.hmdp.mapper.BlogCollectMapper;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.mapper.ContentTopicMapper;
import com.hmdp.mapper.MallProductMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.ContentModerationService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.INoteEventService;
import com.hmdp.service.LikeService;
import com.hmdp.service.IUserNotificationService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.data.redis.connection.RedisListCommands.Direction.last;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService, LikeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;
    @Resource
    private BlogProductMapper blogProductMapper;
    @Resource
    private MallProductMapper mallProductMapper;
    @Resource
    private BlogLikeMapper blogLikeMapper;
    @Resource
    private BlogCollectMapper blogCollectMapper;
    @Resource
    private BlogCommentsMapper blogCommentsMapper;
    @Resource
    private ContentTopicMapper contentTopicMapper;
    @Resource
    private IUserNotificationService notificationService;
    @Resource
    private INoteEventService noteEventService;
    @Resource
    private ContentModerationService contentModerationService;

    private static final Pattern TOPIC_PATTERN = Pattern.compile("#([\\p{IsHan}\\w\\-]{1,30})");
    private static final Pattern AD_PATTERN = Pattern.compile("(加微信|加vx|v信|返现|刷单|兼职|私聊返|平台代理|\\d{6,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABUSE_PATTERN = Pattern.compile("(垃圾|傻[子逼]|滚|死|骗子)");

    /**
     * 保存博文
     * @param blog
     * @return
     */
    @Override
    @Transactional
    public Result saveBlog(Blog blog) {
        if (blog == null) {
            throw new BusinessException(ErrorCode.TITLE_CONTENT_REQUIRED, "笔记内容不能为空");
        }
        List<Long> productIds = normalizeProductIds(blog.getProductIds());
        String contentType = ContentType.resolve(blog.getContentType(), blog.getVideoUrl());
        validateWritePayload(blog, contentType, productIds);
        if (ContentType.PRODUCT_NOTE.name().equals(contentType) && productIds.isEmpty()) {
            throw new BusinessException(ErrorCode.NEED_MOUNT_PRODUCT);
        }
        if (!productIds.isEmpty() && !allProductsOnline(productIds)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ONLINE);
        }
        blog.setContentType(contentType);
        blog.setTags(normalizeTags(blog.getTags()));
        //获取登录用户
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        Long id = currentUser.getId();
        //保存博文
        blog.setUserId(id);
        boolean isSuccess = save(blog);
        //查询粉丝
        if (!isSuccess) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "新增博文失败！");
        }
        saveBlogProducts(blog.getId(), productIds);
        syncBlogTopics(blog);
        List<Follow> follows = followService.query().eq("follow_user_id", id).list();
        //推送粉丝
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(),System.currentTimeMillis());
        }
        //返回id
        return Result.ok(blog.getId());
    }

    @Override
    @Transactional
    public Result updateOwnBlog(Long id, Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (id == null || blog == null) {
            throw new BusinessException(ErrorCode.TITLE_CONTENT_REQUIRED, "笔记内容不能为空");
        }
        Blog existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.BLOG_NOT_EXIST);
        }
        if (!Objects.equals(existing.getUserId(), user.getId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "只能编辑自己的笔记");
        }
        List<Long> productIds = normalizeProductIds(blog.getProductIds());
        String contentType = ContentType.resolve(blog.getContentType(), blog.getVideoUrl());
        validateWritePayload(blog, contentType, productIds);
        if (ContentType.PRODUCT_NOTE.name().equals(contentType) && productIds.isEmpty()) {
            throw new BusinessException(ErrorCode.NEED_MOUNT_PRODUCT);
        }
        if (!productIds.isEmpty() && !allProductsOnline(productIds)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ONLINE);
        }
        update()
                .set("shop_id", blog.getShopId())
                .set("title", StrUtil.trim(blog.getTitle()))
                .set("images", StrUtil.trim(blog.getImages()))
                .set("video_url", StrUtil.trim(blog.getVideoUrl()))
                .set("content_type", contentType)
                .set("tags", normalizeTags(blog.getTags()))
                .set("content", blog.getContent())
                .eq("id", id)
                .eq("user_id", user.getId())
                .update();
        replaceBlogProducts(id, productIds);
        syncBlogTopics(blog);
        return Result.ok(id);
    }

    @Override
    @Transactional
    public Result deleteOwnBlog(Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "笔记ID不能为空");
        }
        Blog blog = getById(id);
        if (blog == null) {
            throw new BusinessException(ErrorCode.BLOG_NOT_EXIST);
        }
        if (!Objects.equals(blog.getUserId(), user.getId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "只能删除自己的笔记");
        }
        removeById(id);
        blogProductMapper.delete(new QueryWrapper<BlogProduct>().eq("blog_id", id));
        blogLikeMapper.delete(new QueryWrapper<BlogLike>().eq("blog_id", id));
        blogCollectMapper.delete(new QueryWrapper<BlogCollect>().eq("blog_id", id));
        blogCommentsMapper.delete(new QueryWrapper<BlogComments>().eq("blog_id", id));
        stringRedisTemplate.delete("blog:liked:" + id);
        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();
        for (Follow follow : follows) {
            stringRedisTemplate.opsForZSet().remove("feed:" + follow.getUserId(), id.toString());
        }
        return Result.ok(id);
    }

    private List<Long> normalizeProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productIds.stream()
                .filter(productId -> productId != null && productId > 0)
                .distinct()
                .limit(6)
                .toList();
    }

    private void validateWritePayload(Blog blog, String contentType, List<Long> productIds) {
        if (StrUtil.isBlank(blog.getTitle()) || StrUtil.isBlank(blog.getContent())) {
            throw new BusinessException(ErrorCode.TITLE_CONTENT_REQUIRED);
        }
        if (StrUtil.trim(blog.getTitle()).length() > 80) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能超过80个字");
        }
        if (blog.getContent().trim().length() > 5000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文不能超过5000个字");
        }
        if (blog.getContent().trim().length() < 12) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文至少需要12个字，补充真实体验后再发布");
        }
        if (isLowQualityText(blog.getTitle(), blog.getContent())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容过于重复，建议补充具体体验、价格、环境或使用感受");
        }
        if (AD_PATTERN.matcher(joinText(blog.getTitle(), blog.getContent(), blog.getTags())).find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容疑似广告、刷评或引流，请修改后再发布");
        }
        if (ABUSE_PATTERN.matcher(joinText(blog.getTitle(), blog.getContent(), blog.getTags())).find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容包含不友善表达，请修改后再发布");
        }
        contentModerationService.checkText("笔记内容", blog.getTitle(), blog.getContent(), blog.getTags());
        boolean videoNote = ContentType.VIDEO.name().equals(contentType) || ContentType.LIVE.name().equals(contentType);
        if (videoNote && StrUtil.isBlank(blog.getVideoUrl())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "视频笔记需要上传视频或填写视频地址");
        }
        if (!videoNote && StrUtil.isBlank(blog.getImages())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图文笔记至少需要一张图片");
        }
        if (ContentType.PRODUCT_NOTE.name().equals(contentType) && blog.getShopId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品种草需要关联店铺");
        }
        if (ContentType.PRODUCT_NOTE.name().equals(contentType) && (productIds == null || productIds.isEmpty())) {
            throw new BusinessException(ErrorCode.NEED_MOUNT_PRODUCT);
        }
    }

    private String normalizeTags(String tags) {
        if (StrUtil.isBlank(tags)) {
            return "";
        }
        return StrUtil.split(tags, ',')
                .stream()
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(tag -> StrUtil.sub(tag.replace("#", ""), 0, 32))
                .distinct()
                .limit(6)
                .collect(Collectors.joining(","));
    }

    private boolean isLowQualityText(String title, String content) {
        String compact = joinText(title, content, "").replaceAll("\\s+", "");
        if (compact.length() < 20) {
            return true;
        }
        long distinctChars = compact.chars().distinct().count();
        return distinctChars <= 6 && compact.length() >= 20;
    }

    private String joinText(String title, String content, String tags) {
        return StrUtil.blankToDefault(title, "") + " " + StrUtil.blankToDefault(content, "") + " " + StrUtil.blankToDefault(tags, "");
    }

    private void syncBlogTopics(Blog blog) {
        if (blog == null) {
            return;
        }
        String searchableText = joinText(blog.getTitle(), blog.getContent(), blog.getTags());
        if (StrUtil.isBlank(searchableText)) {
            return;
        }
        Matcher matcher = TOPIC_PATTERN.matcher(searchableText);
        Set<String> topics = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            topics.add(matcher.group(1));
        }
        if (StrUtil.isNotBlank(blog.getTags())) {
            for (String tag : blog.getTags().split(",")) {
                String keyword = StrUtil.sub(StrUtil.trim(tag), 0, 32);
                if (StrUtil.isNotBlank(keyword)) {
                    topics.add(keyword);
                }
            }
        }
        for (String keyword : topics) {
            ContentTopic topic = contentTopicMapper.selectOne(new QueryWrapper<ContentTopic>().eq("keyword", keyword).last("limit 1"));
            if (topic == null) {
                contentTopicMapper.insert(new ContentTopic()
                        .setKeyword(keyword)
                        .setHeat(1L)
                        .setNoteCount(1L));
            } else {
                contentTopicMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ContentTopic>()
                        .setSql("heat = IFNULL(heat, 0) + 1")
                        .setSql("note_count = IFNULL(note_count, 0) + 1")
                        .eq("id", topic.getId()));
            }
        }
    }

    private void saveBlogProducts(Long blogId, List<Long> productIds) {
        if (blogId == null || productIds.isEmpty()) {
            return;
        }
        for (int index = 0; index < productIds.size(); index++) {
            BlogProduct relation = new BlogProduct()
                    .setBlogId(blogId)
                    .setProductId(productIds.get(index))
                    .setSort(index);
            blogProductMapper.insert(relation);
        }
    }

    private void replaceBlogProducts(Long blogId, List<Long> productIds) {
        if (blogId == null) {
            return;
        }
        blogProductMapper.delete(new QueryWrapper<BlogProduct>().eq("blog_id", blogId));
        saveBlogProducts(blogId, productIds);
    }

    private boolean allProductsOnline(List<Long> productIds) {
        Long count = mallProductMapper.selectCount(
                new QueryWrapper<MallProduct>()
                        .in("id", productIds)
                        .eq("status", 1));
        return count != null && count == productIds.size();
    }

    /**
     * 查询热门博文
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据id查询博文
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog =getById(id);
        if(blog == null){
            throw new BusinessException(ErrorCode.BLOG_NOT_EXIST);
        }
        //查询blog有关的用户
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogLiked(blog);
        return  Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        if(user == null){
            //用户未登录
            return;
        }
        Long userId = user.getId();

        //判断当前用户是否已经点
        String key = "blog:liked:"+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);
    }

    /**
     * 查询blog有关的用户
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 博文点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_LOGIN);
        }
        Long userId = user.getId();
        //判断当前用户是否已经点
        String key = "blog:liked:"+id;
        long likedCount = blogLikeMapper.selectCount(new QueryWrapper<BlogLike>()
                .eq("user_id", userId)
                .eq("blog_id", id));
        if(likedCount == 0) {
            //如果没点赞，点赞（数据库点赞数+1，保存用户到redis集合）
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            if(isSuccess) {
                BlogLike blogLike = new BlogLike()
                        .setUserId(userId)
                        .setBlogId(id);
                blogLikeMapper.insert(blogLike);
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
                Blog blog = getById(id);
                if (blog != null) {
                    notificationService.notifyUser(blog.getUserId(), userId, "LIKE", "有人点赞了你的笔记",
                            "你的笔记《" + StrUtil.blankToDefault(blog.getTitle(), "未命名笔记") + "》收到了新的点赞。", id, null);
                }
                noteEventService.track(userId, id, EventType.LIKE, null, null);
                // 更新用户兴趣画像
                Blog likedBlog = getById(id);
                if (likedBlog != null && StrUtil.isNotBlank(likedBlog.getTags())) {
                    updateUserInterest(userId, likedBlog.getTags(), 3);
                }
            }

        }else{
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            if(isSuccess) {
                blogLikeMapper.delete(new QueryWrapper<BlogLike>()
                        .eq("user_id", userId)
                        .eq("blog_id", id));
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        //如果已经点赞，取消点赞（数据库点赞数-1，redis中set集合中移除）}
        return Result.ok();
    }

    @Override
    public Result likeNote(Long noteId) {
        return likeBlog(noteId);
    }

    /**
     * 查询博文点赞名单
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:"+id;
        //查询前5名点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());

        }
        //解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //根据用户id查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("order by field(id," + StrUtil.join(",", ids) + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        //返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryNoteLikes(Long noteId) {
        return queryBlogLikes(noteId);
    }

    /**
     * 查询关注用户的博文
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(ErrorCode.USER_NOT_LOGIN);
        }
        Long userId = user.getId();
        //查询收件箱
        String key = "feed:"+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 10);
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据：blogId，minTime，offset(一样元素的个数)
        List<Long>ids = new ArrayList<>();
        long minTime =0;
        int os = 1;
        for(ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
            //获取blogId
            String idStr = typedTuple.getValue();

            ids.add(Long.valueOf(idStr));

            //获取分数(时间戳)
            long time = typedTuple.getScore().longValue();
            if(time == minTime){
                os++;
            }else{
                minTime = time;
                os = 1;
            }

        }
        //根据id查询blog

        List<Blog> blogs = query().in("id", ids)
                .last("order by field(id," + StrUtil.join(",", ids) + ")").list();
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            //查询blog是否被点赞
            isBlogLiked(blog);
        }
        //封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }

    /**
     * 根据用户行为更新兴趣画像。
     * 对博客的每个标签增加指定权重，用于个性化推荐。
     */
    private void updateUserInterest(Long userId, String tags, int weight) {
        if (userId == null || StrUtil.isBlank(tags)) return;
        try {
            String key = RedisConstants.USER_INTEREST_KEY + userId;
            for (String tag : tags.split(",")) {
                String trimmed = tag.trim();
                if (StrUtil.isNotBlank(trimmed)) {
                    stringRedisTemplate.opsForZSet().incrementScore(key, trimmed, weight);
                }
            }
            stringRedisTemplate.expire(key, RedisConstants.USER_INTEREST_TTL, java.util.concurrent.TimeUnit.DAYS);
        } catch (Exception e) {
            // 兴趣画像更新失败不影响主流程
        }
    }
}
