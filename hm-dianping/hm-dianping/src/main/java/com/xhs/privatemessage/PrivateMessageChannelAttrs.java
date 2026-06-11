package com.xhs.privatemessage;

import com.xhs.dto.UserDTO;
import io.netty.util.AttributeKey;

public final class PrivateMessageChannelAttrs {

    public static final AttributeKey<UserDTO> USER = AttributeKey.valueOf("privateMessage.user");

    private PrivateMessageChannelAttrs() {
    }
}
