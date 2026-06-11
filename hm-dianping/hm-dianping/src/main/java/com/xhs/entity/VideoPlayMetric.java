package com.xhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视频播放行为指标。
 * 用于沉淀播放时长、最大进度和完播信号，服务视频推荐排序。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_video_play_metric")
public class VideoPlayMetric implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long blogId;
    private Long userId;
    private Integer durationSecond;
    private Integer watchedSecond;
    private Integer maxProgress;
    private Boolean completed;
    private LocalDateTime createTime;
}
