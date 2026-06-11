package com.xhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商城订单。
 * 第一版使用主订单承载商品明细快照，后续可拆成 order/order_item 两张表。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_mall_order")
public class MallOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long merchantId;
    private Long productId;
    private Long skuId;
    private Long addressId;
    private Long voucherId;
    private String productTitle;
    private String productImage;
    private String skuName;
    private String skuSpecs;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String logisticsCompany;
    private String logisticsNo;
    private String refundReason;
    private String refundRemark;
    private Long price;
    private Long discountAmount;
    private Long promotionDiscountAmount;
    private Integer quantity;
    private Long totalAmount;

    /**
     * 订单状态。
     * 1：待支付；2：已支付；3：待发货；4：已发货；5：已完成；6：已取消；7：退款中
     */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime shipTime;
    private LocalDateTime receiveTime;
    private LocalDateTime cancelTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;
}
