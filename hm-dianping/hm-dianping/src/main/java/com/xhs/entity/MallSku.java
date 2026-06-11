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
 * 商品 SKU。
 * specs 使用 JSON 文本保存颜色、尺寸、套餐、口味等规格组合。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_mall_sku")
public class MallSku implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long productId;
    private String skuName;
    private String specs;
    private String image;
    private Long price;
    private Long originPrice;
    private Integer stock;
    private Integer sold;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
