package com.xhs.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Public note card DTO returned to the frontend.
 */
@Data
public class NoteDTO {
    private Long id;
    private Long noteId;
    private Long shopId;
    private Long userId;
    private Long authorId;
    private String title;
    private String cover;
    private String images;
    private String videoUrl;
    private String contentType;
    private String mediaType;
    private String tags;
    private String content;
    private Integer liked;
    private Integer likedCount;
    private Integer comments;
    private Integer commentCount;
    private Integer status;
    private String auditRemark;
    private Long collects;
    private Long collectCount;
    private Long score;
    private String name;
    private String authorName;
    private String icon;
    private String authorIcon;
    private Boolean isLike;
    private Boolean isCollect;
    private Boolean isFollow;
    private Boolean isOwner;
    private Boolean likedByMe;
    private Boolean collected;
    private Boolean followed;
    private LocalDateTime createTime;
}
