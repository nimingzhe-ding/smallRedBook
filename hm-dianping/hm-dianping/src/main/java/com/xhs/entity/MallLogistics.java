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
 * 订单物流信息。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_mall_logistics")
public class MallLogistics implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long merchantId;
    private Long userId;
    private String company;
    private String trackingNo;
    private String status;
    private String traces;
    private LocalDateTime shipTime;
    private LocalDateTime signedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
