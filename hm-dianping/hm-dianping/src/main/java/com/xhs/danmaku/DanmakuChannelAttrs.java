package com.xhs.danmaku;

import com.xhs.dto.UserDTO;
import io.netty.util.AttributeKey;

public final class DanmakuChannelAttrs {

    public static final AttributeKey<Long> VIDEO_ID = AttributeKey.valueOf("danmaku.videoId");
    public static final AttributeKey<UserDTO> USER = AttributeKey.valueOf("danmaku.user");

    private DanmakuChannelAttrs() {
    }
}
