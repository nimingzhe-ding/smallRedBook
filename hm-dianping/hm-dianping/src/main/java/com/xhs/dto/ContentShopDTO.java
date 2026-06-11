package com.xhs.dto;

import lombok.Data;

/**
 * 笔记关联店铺 DTO。
 * 用于在笔记详情里展示店铺、价格、评分和可领取优惠，形成内容到交易的闭环。
 */
@Data
public class ContentShopDTO {
    private Long id;
    private String name;
    private String images;
    private String area;
    private String address;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
    private Long voucherId;
    private String voucherTitle;
    private String voucherSubTitle;
    private Long voucherPayValue;
    private Long voucherActualValue;
}
