package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记评论 DTO。
 * 当前评论接口仍返回 Map 以保持兼容，新增 DTO 作为后续收敛目标。
 */
@Data
public class CommentDTO {
    private Long id;
    private Long noteId;
    private Long userId;
    private Long parentId;
    private Long answerId;
    private String name;
    private String icon;
    private String content;
    private Integer liked;
    private Boolean isOwner;
    private LocalDateTime createTime;
    private List<CommentDTO> replies;
}
