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
 * 商城商品。
 * 第一版商城以内容社区带货为目标，先支持商品展示、库存、价格和销量。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_mall_product")
public class MallProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String title;
    private String subTitle;
    private String images;
    private Long price;
    private Long originPrice;
    private Integer stock;
    private Integer sold;
    private String category;
    private Long categoryId;
    private Long subCategoryId;
    private String specSummary;
    private Integer score;
    private Integer reviewCount;
    private Integer favoriteCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
