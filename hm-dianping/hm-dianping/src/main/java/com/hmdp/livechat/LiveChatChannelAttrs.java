package com.hmdp.livechat;

import com.hmdp.dto.UserDTO;
import io.netty.util.AttributeKey;

public final class LiveChatChannelAttrs {

    public static final AttributeKey<Long> ROOM_ID = AttributeKey.valueOf("liveChat.roomId");
    public static final AttributeKey<UserDTO> USER = AttributeKey.valueOf("liveChat.user");

    private LiveChatChannelAttrs() {
    }
}
