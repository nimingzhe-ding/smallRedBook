package com.xhs.danmaku;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DanmakuEvent {

    private String type = "danmaku";
    private Long id;
    private Long messageId;
    private String requestId;
    private Long videoId;
    private Long blogId;
    private Long userId;
    private String content;
    private Integer videoSecond;
    private Integer lane;
    private Long createTime;
}
