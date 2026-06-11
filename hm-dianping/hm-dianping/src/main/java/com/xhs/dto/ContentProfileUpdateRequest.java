package com.xhs.dto;

import lombok.Data;

/**
 * 个人主页资料编辑请求。
 */
@Data
public class ContentProfileUpdateRequest {
    private String nickName;
    private String icon;
    private String introduce;
    private String city;
}
