package com.xhs.dto;

import lombok.Data;

/**
 * 购物车展示 DTO。
 * 合并购物车数量和商品快照，前端无需再二次查询商品。
 */
@Data
public class MallCartItemDTO {
    private Long id;
    private Long productId;
    private String title;
    private String image;
    private Long price;
    private Integer quantity;
    private Long totalAmount;
}
