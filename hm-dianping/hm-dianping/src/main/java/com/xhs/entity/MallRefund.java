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
 * 售后退款单。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_mall_refund")
public class MallRefund implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private Long merchantId;
    private Long amount;
    private String reason;
    private String images;
    private Integer status;
    private String merchantRemark;
    private LocalDateTime applyTime;
    private LocalDateTime handleTime;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
