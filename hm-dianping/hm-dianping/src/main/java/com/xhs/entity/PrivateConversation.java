package com.xhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_private_conversation")
public class PrivateConversation implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userLowId;
    private Long userHighId;
    private Long lastMessageId;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Integer lowUnreadCount;
    private Integer highUnreadCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
