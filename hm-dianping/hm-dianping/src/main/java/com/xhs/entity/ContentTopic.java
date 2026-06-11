package com.xhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 话题：由 #话题 从内容里沉淀出来，支持热度、笔记数和趋势榜。
 */
@Data
@Accessors(chain = true)
@TableName("tb_content_topic")
public class ContentTopic {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String keyword;
    private Long heat;
    private Long noteCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
