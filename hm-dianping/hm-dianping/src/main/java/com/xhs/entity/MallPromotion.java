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
 * 商城营销活动。
 * type 支持 FULL_REDUCTION、LIMITED_DISCOUNT、SECKILL、BUNDLE。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_mall_promotion")
public class MallPromotion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private Long productId;
    private String type;
    private String title;
    private Long thresholdAmount;
    private Long discountAmount;
    private Integer discountRate;
    private String bundleProductIds;
    private Integer status;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
