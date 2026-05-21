package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Public note card DTO returned to the frontend.
 */
@Data
public class NoteDTO {
    private Long id;
    private Long shopId;
    private Long userId;
    private String title;
    private String images;
    private String videoUrl;
    private String contentType;
    private String tags;
    private String content;
    private Integer liked;
    private Integer comments;
    private Integer status;
    private String auditRemark;
    private Long collects;
    private String name;
    private String icon;
    private Boolean isLike;
    private Boolean isCollect;
    private Boolean isFollow;
    private Boolean isOwner;
    private LocalDateTime createTime;
}
