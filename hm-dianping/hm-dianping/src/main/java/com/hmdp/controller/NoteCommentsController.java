package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.NoteCommentRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.enums.ErrorCode;
import com.hmdp.enums.EventType;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.INoteEventService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IUserNotificationService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论控制器：负责笔记详情页评论流、二级回复、删除、举报和排序。
 */
@RestController
@RequestMapping("/notes/comments")
public class NoteCommentsController {

    @Resource
    private IBlogCommentsService commentsService;

    @Resource
    private IUserService userService;

    @Resource
    private IBlogService blogService;

    @Resource
    private IUserNotificationService notificationService;

    @Resource
    private INoteEventService noteEventService;

    /**
     * 查询笔记评论流。
     * sort 支持 hot/new/old：热门、最新、最早。
     */
    @GetMapping({"/of/note", "/of/blog"})
    public Result queryComments(
            @RequestParam(value = "noteId", required = false) Long noteId,
            @RequestParam(value = "blogId", required = false) Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "sort", defaultValue = "hot") String sort) {
        Long resolvedNoteId = resolveNoteId(noteId, blogId);
        QueryChainWrapper<BlogComments> query = commentsService.query()
                .eq("blog_id", resolvedNoteId)
                .eq("parent_id", 0)
                .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"));
        if ("new".equals(sort)) {
            query.orderByDesc("create_time");
        } else if ("old".equals(sort)) {
            query.orderByAsc("create_time");
        } else {
            query.orderByDesc("liked").orderByDesc("create_time");
        }
        Page<BlogComments> page = query.page(new Page<>(current, 20));
        List<BlogComments> comments = page.getRecords();
        List<Long> parentIds = comments.stream()
                .map(BlogComments::getId)
                .toList();
        List<BlogComments> replies = parentIds.isEmpty()
                ? List.of()
                : commentsService.query()
                .eq("blog_id", resolvedNoteId)
                .in("parent_id", parentIds)
                .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"))
                .orderByAsc("create_time")
                .list();
        List<BlogComments> allComments = new ArrayList<>();
        allComments.addAll(comments);
        allComments.addAll(replies);
        List<Long> userIds = allComments.stream()
                .map(BlogComments::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        Map<Long, List<Map<String, Object>>> replyMap = replies.stream()
                .map(reply -> toCommentMap(reply, userMap, List.of()))
                .collect(Collectors.groupingBy(reply -> (Long) reply.get("parentId"), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> records = comments.stream()
                .map(comment -> toCommentMap(comment, userMap, replyMap.getOrDefault(comment.getId(), List.of())))
                .toList();
        return Result.ok(records, countVisibleComments(resolvedNoteId));
    }

    /**
     * 发表评论或二级回复。
     */
    @PostMapping
    public Result saveComment(@RequestBody NoteCommentRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        BlogComments comment = new BlogComments();
        comment.setBlogId(request == null ? null : request.getNoteId());
        comment.setContent(request == null ? null : request.getContent());
        comment.setParentId(request == null ? null : request.getParentId());
        comment.setAnswerId(request == null ? null : request.getAnswerId());
        if (comment.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "笔记ID不能为空");
        }
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "评论内容不能为空");
        }
        comment.setId(null);
        comment.setUserId(user.getId());
        Long parentId = comment.getParentId() == null ? 0L : comment.getParentId();
        Long answerId = comment.getAnswerId() == null ? parentId : comment.getAnswerId();
        if (parentId > 0) {
            BlogComments parentComment = commentsService.getById(parentId);
            if (parentComment == null || !comment.getBlogId().equals(parentComment.getBlogId()) || !isVisible(parentComment)) {
                throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "回复的评论不存在");
            }
        }
        comment.setParentId(parentId);
        comment.setAnswerId(answerId);
        comment.setLiked(0);
        comment.setStatus(0);
        commentsService.save(comment);
        blogService.update()
                .setSql("comments = IFNULL(comments, 0) + 1")
                .eq("id", comment.getBlogId())
                .update();
        notifyCommentReceivers(comment, user.getId());
        noteEventService.track(user.getId(), comment.getBlogId(), EventType.COMMENT, null, null);
        return Result.ok(commentResult(comment.getId(), comment.getBlogId()));
    }

    private void notifyCommentReceivers(BlogComments comment, Long actorUserId) {
        var blog = blogService.getById(comment.getBlogId());
        String title = blog == null ? "未命名笔记" : Objects.toString(blog.getTitle(), "未命名笔记");
        if (blog != null) {
            notificationService.notifyUser(blog.getUserId(), actorUserId, "COMMENT", "你的笔记有新评论",
                    "你的笔记《" + title + "》收到了新评论：" + comment.getContent(), comment.getBlogId(), null);
        }
        if (comment.getAnswerId() != null && comment.getAnswerId() > 0) {
            BlogComments answer = commentsService.getById(comment.getAnswerId());
            if (answer != null) {
                notificationService.notifyUser(answer.getUserId(), actorUserId, "REPLY", "有人回复了你的评论",
                        "你在《" + title + "》下的评论收到了回复：" + comment.getContent(), comment.getBlogId(), null);
            }
        }
    }

    /**
     * 评论点赞。当前轻量实现只维护点赞数，不记录用户点赞明细。
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long commentId) {
        if (UserHolder.getUser() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (commentId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "评论ID不能为空");
        }
        boolean success = commentsService.update()
                .setSql("liked = IFNULL(liked, 0) + 1")
                .eq("id", commentId)
                .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"))
                .update();
        if (!success) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "评论不存在");
        }
        BlogComments comment = commentsService.getById(commentId);
        return Result.ok(comment == null ? 0 : comment.getLiked());
    }

    /**
     * 删除评论：软删除，一级评论会连同二级回复一起隐藏。
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        BlogComments comment = commentsService.getById(commentId);
        if (!isVisible(comment)) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "评论不存在");
        }
        if (!comment.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权删除");
        }
        long affected = updateCommentThreadStatus(comment, 2);
        decreaseBlogCommentCount(comment.getBlogId(), affected);
        return Result.ok(commentResult(commentId, comment.getBlogId()));
    }

    /**
     * 举报评论：将评论标记为被举报并从前台隐藏，等待后续运营审核。
     */
    @PutMapping("/report/{id}")
    public Result reportComment(@PathVariable("id") Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        BlogComments comment = commentsService.getById(commentId);
        if (!isVisible(comment)) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "评论不存在");
        }
        if (comment.getUserId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能举报自己的评论");
        }
        long affected = updateCommentThreadStatus(comment, 1);
        decreaseBlogCommentCount(comment.getBlogId(), affected);
        return Result.ok(commentResult(commentId, comment.getBlogId()));
    }

    private Map<String, Object> toCommentMap(BlogComments comment, Map<Long, User> userMap, List<Map<String, Object>> replies) {
        User user = userMap.get(comment.getUserId());
        UserDTO current = UserHolder.getUser();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("noteId", comment.getBlogId());
        result.put("userId", comment.getUserId());
        result.put("parentId", comment.getParentId());
        result.put("answerId", comment.getAnswerId());
        result.put("name", user == null ? "探店用户" : user.getNickName());
        result.put("icon", user == null ? "" : Objects.toString(user.getIcon(), ""));
        result.put("content", comment.getContent());
        result.put("liked", comment.getLiked() == null ? 0 : comment.getLiked());
        result.put("isOwner", current != null && comment.getUserId() != null && comment.getUserId().equals(current.getId()));
        result.put("createTime", comment.getCreateTime());
        result.put("replies", replies);
        return result;
    }

    private boolean isVisible(BlogComments comment) {
        return comment != null && (comment.getStatus() == null || Objects.equals(comment.getStatus(), 0));
    }

    private Long countVisibleComments(Long blogId) {
        return commentsService.query()
                .eq("blog_id", blogId)
                .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"))
                .count();
    }

    private Map<String, Object> commentResult(Long id, Long blogId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("noteId", blogId);
        result.put("comments", countVisibleComments(blogId));
        return result;
    }

    private Long resolveNoteId(Long noteId, Long legacyBlogId) {
        Long resolved = noteId == null ? legacyBlogId : noteId;
        if (resolved == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "绗旇ID涓嶈兘涓虹┖");
        }
        return resolved;
    }

    private long updateCommentThreadStatus(BlogComments comment, int status) {
        List<Long> ids = new ArrayList<>();
        ids.add(comment.getId());
        if (comment.getParentId() != null && comment.getParentId() == 0) {
            List<Long> replyIds = commentsService.query()
                    .select("id")
                    .eq("blog_id", comment.getBlogId())
                    .eq("parent_id", comment.getId())
                    .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"))
                    .list()
                    .stream()
                    .map(BlogComments::getId)
                    .toList();
            ids.addAll(replyIds);
        }
        commentsService.update()
                .set("status", status)
                .set("update_time", LocalDateTime.now())
                .in("id", ids)
                .and(wrapper -> wrapper.eq("status", 0).or().isNull("status"))
                .update();
        return ids.size();
    }

    private void decreaseBlogCommentCount(Long blogId, long count) {
        if (count <= 0) {
            return;
        }
        blogService.update()
                .setSql("comments = GREATEST(IFNULL(comments, 0) - " + count + ", 0)")
                .eq("id", blogId)
                .update();
    }
}
