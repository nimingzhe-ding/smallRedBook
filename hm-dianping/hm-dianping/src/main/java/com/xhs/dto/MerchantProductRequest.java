package com.xhs.dto;

import lombok.Data;

/**
 * 商家商品发布/编辑请求。
 */
@Data
public class MerchantProductRequest {
    private String title;
    private String subTitle;
    private String images;
    private Long price;
    private Long originPrice;
    private Integer stock;
    private String category;
    private Long categoryId;
    private Long subCategoryId;
    private String specSummary;
    private Integer status;
}
