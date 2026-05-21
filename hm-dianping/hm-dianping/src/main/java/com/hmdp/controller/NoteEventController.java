package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.NoteEvent;
import com.hmdp.enums.ErrorCode;
import com.hmdp.enums.EventType;
import com.hmdp.exception.BusinessException;
import com.hmdp.service.INoteEventService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 笔记行为采集接口。
 * 前端用来记录曝光、点击、搜索等事件，后续可用于个性化推荐和数据看板。
 */
@RestController
@RequestMapping("/note-event")
public class NoteEventController {

    @Resource
    private INoteEventService noteEventService;

    @PostMapping
    public Result track(@RequestBody NoteEvent event) {
        UserDTO user = UserHolder.getUser();
        Long userId = user == null ? null : user.getId();
        EventType type;
        try {
            type = EventType.valueOf(event.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的事件类型: " + event.getEventType());
        }
        Long noteId = event.getNoteId() == null ? event.getBlogId() : event.getNoteId();
        noteEventService.track(userId, noteId, type, event.getScene(), event.getKeyword());
        return Result.ok();
    }

    @PostMapping("/batch")
    public Result trackBatch(@RequestBody List<NoteEvent> events) {
        UserDTO user = UserHolder.getUser();
        Long userId = user == null ? null : user.getId();
        for (NoteEvent event : events) {
            event.setUserId(userId);
            if (event.getBlogId() == null) {
                event.setBlogId(event.getNoteId());
            }
        }
        noteEventService.trackBatch(events);
        return Result.ok();
    }
}
