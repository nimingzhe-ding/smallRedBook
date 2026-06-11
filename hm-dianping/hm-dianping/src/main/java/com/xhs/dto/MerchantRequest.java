package com.xhs.dto;

import lombok.Data;

/**
 * 商家入驻/资料更新请求。
 */
@Data
public class MerchantRequest {
    private String name;
    private String avatar;
    private String description;
    private String phone;
    private String address;
}
