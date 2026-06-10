package com.hmdp.livechat;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class LiveChatEvent {

    private String type = "danmaku";
    private Long id;
    private Long messageId;
    private String requestId;
    private Long roomId;
    private Long userId;
    private String content;
    private Integer liked;
    private Integer status;
    private Long createTime;
}
