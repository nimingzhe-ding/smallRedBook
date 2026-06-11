package com.xhs.dto;

import lombok.Data;

/**
 * 内容社区个人面板 DTO。
 * 聚合用户基础信息、创作者数据和社交关系，供首页右侧面板、我的主页、他人主页复用。
 */
@Data
public class ContentProfileDTO {
    private Long userId;
    private String nickName;
    private String icon;
    private String introduce;
    private String city;
    private Long notes;
    private Long likes;
    private Long collects;
    private Long followers;
    private Long following;
    private Boolean isMe;
    private Boolean isFollow;
    private CreatorGrowthDTO creatorGrowth;
}
