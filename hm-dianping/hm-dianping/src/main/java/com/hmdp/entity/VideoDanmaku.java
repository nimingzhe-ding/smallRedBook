package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 视频弹幕。
 * 弹幕对外匿名展示，只绑定笔记和视频播放秒数。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_video_danmaku")
public class VideoDanmaku implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long blogId;
    private Long userId;
    private String content;
    private Integer videoSecond;
    private Integer lane;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
