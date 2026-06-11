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
 * 商家通知。
 * 记录支付、退款等业务事件，供商家后台实时轮询或推送。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_merchant_notification")
public class MerchantNotification implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商家用户 id
     */
    private Long merchantId;

    /**
     * 通知类型：order_paid / order_refund
     */
    private String type;

    /**
     * 关联订单 id
     */
    private Long orderId;

    /**
     * JSON 格式的业务数据
     */
    private String payload;

    /**
     * 0：未读；1：已读
     */
    private Boolean readFlag;

    private LocalDateTime createTime;
}
