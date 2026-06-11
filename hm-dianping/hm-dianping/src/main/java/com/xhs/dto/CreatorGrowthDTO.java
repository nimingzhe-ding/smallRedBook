package com.xhs.dto;

import lombok.Data;

import java.util.List;

/**
 * 创作者成长信息：等级、徽章、连续发布和优质创作者标识。
 */
@Data
public class CreatorGrowthDTO {
    private Integer level;
    private String levelName;
    private Long score;
    private Integer continuousPublishDays;
    private Boolean qualityCreator;
    private List<String> badges;
}
