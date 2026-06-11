package com.xhs.dto;

import lombok.Data;

/**
 * 用户通知设置更新请求。
 */
@Data
public class UserNotificationSettingRequest {
    private Boolean interactionEnabled;
    private Boolean orderEnabled;
    private Boolean auditEnabled;
    private Boolean systemEnabled;
    private Boolean realtimeEnabled;
}
