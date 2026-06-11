package com.xhs.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xhs.annotation.RequireRole;
import com.xhs.dto.ContentReviewRequest;
import com.xhs.dto.Result;
import com.xhs.entity.Blog;
import com.xhs.entity.BlogComments;
import com.xhs.entity.ContentAuditLog;
import com.xhs.entity.MallOrder;
import com.xhs.entity.MallProduct;
import com.xhs.entity.NoteEvent;
import com.xhs.entity.User;
import com.xhs.entity.VideoDanmaku;
import com.xhs.enums.ErrorCode;
import com.xhs.enums.UserRole;
import com.xhs.exception.BusinessException;
import com.xhs.mapper.BlogCommentsMapper;
import com.xhs.mapper.BlogMapper;
import com.xhs.mapper.ContentAuditLogMapper;
import com.xhs.mapper.MallOrderMapper;
import com.xhs.mapper.MallProductMapper;
import com.xhs.mapper.NoteEventMapper;
import com.xhs.mapper.UserMapper;
import com.xhs.mapper.VideoDanmakuMapper;
import com.xhs.service.IUserNotificationService;
import com.xhs.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运营后台接口。
 * 先覆盖内容治理最小闭环：概览、举报评论队列、恢复/隐藏处理。
 */
@RestController
@RequestMapping("/admin")
@RequireRole(UserRole.ADMIN)
public class AdminController {

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private BlogCommentsMapper blogCommentsMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private NoteEventMapper noteEventMapper;
    @Resource
    private MallProductMapper mallProductMapper;
    @Resource
    private MallOrderMapper mallOrderMapper;
    @Resource
    private VideoDanmakuMapper videoDanmakuMapper;
    @Resource
    private IUserNotificationService notificationService;
    @Resource
    private ContentAuditLogMapper auditLogMapper;

    @GetMapping("/dashboard")
    public Result dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notes", blogMapper.selectCount(new QueryWrapper<>()));
        result.put("reportedNotes", blogMapper.selectCount(new QueryWrapper<Blog>().eq("status", 1)));
        result.put("hiddenNotes", blogMapper.selectCount(new QueryWrapper<Blog>().eq("status", 2)));
        result.put("reportedComments", blogCommentsMapper.selectCount(new QueryWrapper<BlogComments>().eq("status", 1)));
        result.put("hiddenComments", blogCommentsMapper.selectCount(new QueryWrapper<BlogComments>().eq("status", 2)));
        result.put("reportedDanmaku", videoDanmakuMapper.selectCount(new QueryWrapper<VideoDanmaku>().eq("status", 1)));
        result.put("hiddenDanmaku", videoDanmakuMapper.selectCount(new QueryWrapper<VideoDanmaku>().eq("status", 2)));
        result.put("events", noteEventMapper.selectCount(new QueryWrapper<>()));
        result.put("products", mallProductMapper.selectCount(new QueryWrapper<MallProduct>().eq("status", 1)));
        result.put("orders", mallOrderMapper.selectCount(new QueryWrapper<>()));
        result.put("searches", noteEventMapper.selectCount(new QueryWrapper<NoteEvent>().eq("event_type", "SEARCH")));
        return Result.ok(result);
    }

    @GetMapping("/comments/reported")
    public Result reportedComments(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<BlogComments> page =
                blogCommentsMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, 20),
                        new QueryWrapper<BlogComments>()
                                .eq("status", 1)
                                .orderByDesc("update_time")
                                .orderByDesc("create_time"));
        List<BlogComments> comments = page.getRecords();
        List<Long> userIds = comments.stream()
                .map(BlogComments::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Long> blogIds = comments.stream()
                .map(BlogComments::getBlogId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        Map<Long, Blog> blogMap = blogIds.isEmpty() ? Map.of() : blogMapper.selectBatchIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, Function.identity(), (first, second) -> first));

        List<Map<String, Object>> records = comments.stream()
                .map(comment -> toReportedComment(comment, userMap, blogMap))
                .toList();
        return Result.ok(records, page.getTotal());
    }

    @GetMapping("/notes/reported")
    public Result reportedNotes(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Blog> page =
                blogMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, 20),
                        new QueryWrapper<Blog>()
                                .eq("status", 1)
                                .orderByDesc("update_time")
                                .orderByDesc("create_time"));
        List<Blog> notes = page.getRecords();
        List<Long> userIds = notes.stream()
                .map(Blog::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        List<Map<String, Object>> records = notes.stream()
                .map(note -> toReportedNote(note, userMap))
                .toList();
        return Result.ok(records, page.getTotal());
    }

    @GetMapping("/danmaku/reported")
    public Result reportedDanmaku(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<VideoDanmaku> page =
                videoDanmakuMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, 20),
                        new QueryWrapper<VideoDanmaku>()
                                .eq("status", 1)
                                .orderByDesc("update_time")
                                .orderByDesc("create_time"));
        List<VideoDanmaku> danmakuList = page.getRecords();
        List<Long> userIds = danmakuList.stream()
                .map(VideoDanmaku::getUserId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<Long> blogIds = danmakuList.stream()
                .map(VideoDanmaku::getBlogId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        Map<Long, Blog> blogMap = blogIds.isEmpty() ? Map.of() : blogMapper.selectBatchIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, Function.identity(), (first, second) -> first));
        List<Map<String, Object>> records = danmakuList.stream()
                .map(danmaku -> toReportedDanmaku(danmaku, userMap, blogMap))
                .toList();
        return Result.ok(records, page.getTotal());
    }

    @PostMapping("/comments/{id}/restore")
    public Result restoreComment(@PathVariable("id") Long commentId,
                                 @RequestBody(required = false) ContentReviewRequest request) {
        BlogComments comment = requireComment(commentId);
        if (comment.getStatus() == null || comment.getStatus() == 0) {
            return Result.ok(commentId);
        }
        String remark = reviewRemark(request, "恢复展示");
        blogCommentsMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BlogComments>()
                .set("status", 0)
                .set("audit_remark", remark)
                .set("auditor_id", currentAdminId())
                .set("audit_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .eq("id", commentId));
        saveAuditLog("COMMENT", comment.getId(), comment.getBlogId(), comment.getReporterId(), comment.getUserId(), "RESTORE",
                comment.getReportReason(), remark);
        notifyCommentAudit(comment, true);
        return Result.ok(commentId);
    }

    @PostMapping("/comments/{id}/hide")
    public Result hideComment(@PathVariable("id") Long commentId,
                              @RequestBody(required = false) ContentReviewRequest request) {
        BlogComments comment = requireComment(commentId);
        if (comment.getStatus() != null && comment.getStatus() == 2) {
            return Result.ok(commentId);
        }
        String remark = reviewRemark(request, "违反社区规范");
        blogCommentsMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<BlogComments>()
                .set("status", 2)
                .set("audit_remark", remark)
                .set("auditor_id", currentAdminId())
                .set("audit_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .eq("id", commentId));
        saveAuditLog("COMMENT", comment.getId(), comment.getBlogId(), comment.getReporterId(), comment.getUserId(), "HIDE",
                comment.getReportReason(), remark);
        notifyCommentAudit(comment, false);
        return Result.ok(commentId);
    }

    @PostMapping("/notes/{id}/restore")
    public Result restoreNote(@PathVariable("id") Long noteId,
                              @RequestBody(required = false) ContentReviewRequest request) {
        Blog note = requireNote(noteId);
        String remark = reviewRemark(request, "恢复展示");
        blogMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Blog>()
                .set("status", 0)
                .set("audit_remark", remark)
                .set("auditor_id", currentAdminId())
                .set("audit_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .eq("id", noteId));
        saveAuditLog("NOTE", note.getId(), note.getId(), note.getReporterId(), note.getUserId(), "RESTORE",
                note.getReportReason(), remark);
        notifyNoteAudit(note, true);
        return Result.ok(noteId);
    }

    @PostMapping("/notes/{id}/hide")
    public Result hideNote(@PathVariable("id") Long noteId,
                           @RequestBody(required = false) ContentReviewRequest request) {
        Blog note = requireNote(noteId);
        String remark = reviewRemark(request, "违反社区规范");
        blogMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Blog>()
                .set("status", 2)
                .set("audit_remark", remark)
                .set("auditor_id", currentAdminId())
                .set("audit_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .eq("id", noteId));
        saveAuditLog("NOTE", note.getId(), note.getId(), note.getReporterId(), note.getUserId(), "HIDE",
                note.getReportReason(), remark);
        notifyNoteAudit(note, false);
        return Result.ok(noteId);
    }

    @PostMapping("/danmaku/{id}/restore")
    public Result restoreDanmaku(@PathVariable("id") Long danmakuId,
                                 @RequestBody(required = false) ContentReviewRequest request) {
        VideoDanmaku danmaku = requireDanmaku(danmakuId);
        String remark = reviewRemark(request, "恢复展示");
        videoDanmakuMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<VideoDanmaku>()
                .set("status", 0)
                .set("audit_remark", remark)
                .set("auditor_id", currentAdminId())
                .set("audit_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .eq("id", danmakuId));
        saveAuditLog("DANMAKU", danmaku.getId(), danmaku.getBlogId(), danmaku.getReporterId(), danmaku.getUserId(), "RESTORE",
                danmaku.getReportReason(), remark);
        notifyDanmakuAudit(danmaku, true);
        return Result.ok(danmakuId);
    }

    @PostMapping("/danmaku/{id}/hide")
    public Result hideDanmaku(@PathVariable("id") Long danmakuId,
                              @RequestBody(required = false) ContentReviewRequest request) {
        VideoDanmaku danmaku = requireDanmaku(danmakuId);
        String remark = reviewRemark(request, "违反社区规范");
        videoDanmakuMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<VideoDanmaku>()
                .set("status", 2)
                .set("audit_remark", remark)
                .set("auditor_id", currentAdminId())
                .set("audit_time", LocalDateTime.now())
                .set("update_time", LocalDateTime.now())
                .eq("id", danmakuId));
        saveAuditLog("DANMAKU", danmaku.getId(), danmaku.getBlogId(), danmaku.getReporterId(), danmaku.getUserId(), "HIDE",
                danmaku.getReportReason(), remark);
        notifyDanmakuAudit(danmaku, false);
        return Result.ok(danmakuId);
    }

    private BlogComments requireComment(Long commentId) {
        if (commentId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "评论ID不能为空");
        }
        BlogComments comment = blogCommentsMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "评论不存在");
        }
        return comment;
    }

    private Blog requireNote(Long noteId) {
        if (noteId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "笔记ID不能为空");
        }
        Blog note = blogMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "笔记不存在");
        }
        return note;
    }

    private VideoDanmaku requireDanmaku(Long danmakuId) {
        if (danmakuId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "弹幕ID不能为空");
        }
        VideoDanmaku danmaku = videoDanmakuMapper.selectById(danmakuId);
        if (danmaku == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "弹幕不存在");
        }
        return danmaku;
    }

    private Map<String, Object> toReportedComment(BlogComments comment, Map<Long, User> userMap, Map<Long, Blog> blogMap) {
        User user = userMap.get(comment.getUserId());
        Blog blog = blogMap.get(comment.getBlogId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", comment.getId());
        item.put("noteId", comment.getBlogId());
        item.put("noteTitle", blog == null ? "未命名笔记" : blog.getTitle());
        item.put("userId", comment.getUserId());
        item.put("userName", user == null ? "探店用户" : user.getNickName());
        item.put("content", comment.getContent());
        item.put("liked", comment.getLiked() == null ? 0 : comment.getLiked());
        item.put("status", comment.getStatus());
        item.put("reportReason", comment.getReportReason());
        item.put("reportCount", comment.getReportCount() == null ? 0 : comment.getReportCount());
        item.put("auditRemark", comment.getAuditRemark());
        item.put("auditTime", comment.getAuditTime());
        item.put("createTime", comment.getCreateTime());
        item.put("updateTime", comment.getUpdateTime());
        return item;
    }

    private Map<String, Object> toReportedNote(Blog note, Map<Long, User> userMap) {
        User user = userMap.get(note.getUserId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", note.getId());
        item.put("noteId", note.getId());
        item.put("title", note.getTitle());
        item.put("content", note.getContent());
        item.put("userId", note.getUserId());
        item.put("userName", user == null ? "探店用户" : user.getNickName());
        item.put("liked", note.getLiked() == null ? 0 : note.getLiked());
        item.put("comments", note.getComments() == null ? 0 : note.getComments());
        item.put("status", note.getStatus());
        item.put("reportReason", note.getReportReason());
        item.put("reportCount", note.getReportCount() == null ? 0 : note.getReportCount());
        item.put("auditRemark", note.getAuditRemark());
        item.put("auditTime", note.getAuditTime());
        item.put("createTime", note.getCreateTime());
        item.put("updateTime", note.getUpdateTime());
        return item;
    }

    private Map<String, Object> toReportedDanmaku(VideoDanmaku danmaku, Map<Long, User> userMap, Map<Long, Blog> blogMap) {
        User user = userMap.get(danmaku.getUserId());
        Blog blog = blogMap.get(danmaku.getBlogId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", danmaku.getId());
        item.put("noteId", danmaku.getBlogId());
        item.put("noteTitle", blog == null ? "未命名笔记" : blog.getTitle());
        item.put("userId", danmaku.getUserId());
        item.put("userName", user == null ? "探店用户" : user.getNickName());
        item.put("content", danmaku.getContent());
        item.put("videoSecond", danmaku.getVideoSecond() == null ? 0 : danmaku.getVideoSecond());
        item.put("lane", danmaku.getLane() == null ? 0 : danmaku.getLane());
        item.put("status", danmaku.getStatus());
        item.put("reportReason", danmaku.getReportReason());
        item.put("reportCount", danmaku.getReportCount() == null ? 0 : danmaku.getReportCount());
        item.put("auditRemark", danmaku.getAuditRemark());
        item.put("auditTime", danmaku.getAuditTime());
        item.put("createTime", danmaku.getCreateTime());
        item.put("updateTime", danmaku.getUpdateTime());
        return item;
    }

    private String reviewRemark(ContentReviewRequest request, String fallback) {
        String remark = request == null ? null : request.getRemark();
        if (remark == null || remark.isBlank()) {
            return fallback;
        }
        return remark.trim().length() > 255 ? remark.trim().substring(0, 255) : remark.trim();
    }

    private Long currentAdminId() {
        return UserHolder.getUser() == null ? null : UserHolder.getUser().getId();
    }

    private void saveAuditLog(String targetType, Long targetId, Long noteId, Long reporterId, Long targetUserId,
                              String action, String reason, String remark) {
        auditLogMapper.insert(new ContentAuditLog()
                .setTargetType(targetType)
                .setTargetId(targetId)
                .setNoteId(noteId)
                .setReporterId(reporterId)
                .setTargetUserId(targetUserId)
                .setAuditorId(currentAdminId())
                .setAction(action)
                .setReason(reason)
                .setRemark(remark)
                .setCreateTime(LocalDateTime.now()));
    }

    private void notifyNoteAudit(Blog note, boolean restored) {
        if (note == null || note.getUserId() == null) {
            return;
        }
        String title = note.getTitle() == null || note.getTitle().isBlank() ? "未命名笔记" : note.getTitle();
        notificationService.notifyUser(
                note.getUserId(),
                null,
                restored ? "AUDIT_NOTE_RESTORED" : "AUDIT_NOTE_HIDDEN",
                restored ? "你的笔记已恢复展示" : "你的笔记已被隐藏",
                restored ? "笔记《" + title + "》已通过审核并恢复展示。"
                        : "笔记《" + title + "》因违反社区规范已被隐藏，请修改后再发布。",
                note.getId(),
                null,
                "{\"auditTarget\":\"NOTE\",\"noteId\":" + note.getId()
                        + ",\"action\":\"" + (restored ? "RESTORED" : "HIDDEN") + "\"}");
    }

    private void notifyCommentAudit(BlogComments comment, boolean restored) {
        if (comment == null || comment.getUserId() == null) {
            return;
        }
        notificationService.notifyUser(
                comment.getUserId(),
                null,
                restored ? "AUDIT_COMMENT_RESTORED" : "AUDIT_COMMENT_HIDDEN",
                restored ? "你的评论已恢复展示" : "你的评论已被隐藏",
                restored ? "你的评论已通过审核并恢复展示。"
                        : "你的评论因违反社区规范已被隐藏，请注意友善交流。",
                comment.getBlogId(),
                null,
                "{\"auditTarget\":\"COMMENT\",\"commentId\":" + comment.getId()
                        + ",\"noteId\":" + comment.getBlogId()
                        + ",\"action\":\"" + (restored ? "RESTORED" : "HIDDEN") + "\"}");
    }

    private void notifyDanmakuAudit(VideoDanmaku danmaku, boolean restored) {
        if (danmaku == null || danmaku.getUserId() == null) {
            return;
        }
        notificationService.notifyUser(
                danmaku.getUserId(),
                null,
                restored ? "AUDIT_DANMAKU_RESTORED" : "AUDIT_DANMAKU_HIDDEN",
                restored ? "你的弹幕已恢复展示" : "你的弹幕已被隐藏",
                restored ? "你的弹幕已通过审核并恢复展示。"
                        : "你的弹幕因违反社区规范已被隐藏，请注意表达方式。",
                danmaku.getBlogId(),
                null,
                "{\"auditTarget\":\"DANMAKU\",\"danmakuId\":" + danmaku.getId()
                        + ",\"noteId\":" + danmaku.getBlogId()
                        + ",\"action\":\"" + (restored ? "RESTORED" : "HIDDEN") + "\"}");
    }
}
