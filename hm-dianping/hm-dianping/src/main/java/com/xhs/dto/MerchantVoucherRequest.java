package com.xhs.dto;

import lombok.Data;

/**
 * 商家创建商城优惠券请求。
 */
@Data
public class MerchantVoucherRequest {
    private Long productId;
    private String scopeType;
    private Long categoryId;
    private Long platformId;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
}
