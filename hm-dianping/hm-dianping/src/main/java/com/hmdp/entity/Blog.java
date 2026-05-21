package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hmdp.enums.ContentType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 商户id
     */
    private Long shopId;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 用户图标
     */
    @TableField(exist = false)
    private String icon;
    /**
     * 用户姓名
     */
    @TableField(exist = false)
    private String name;
    /**
     * 是否点赞过了
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * 标题
     */
    private String title;

    /**
     * 探店的照片，最多9张，多张以","隔开
     */
    private String images;

    /**
     * 探店的文字描述
     */
    private String videoUrl;

    /**
     * 内容类型：IMAGE、VIDEO、LIVE、PRODUCT_NOTE
     */
    private String contentType;

    /**
     * 内容标签，多个标签用英文逗号分隔，例如：美食,探店,好物
     */
    private String tags;

    private String content;

    /**
     * 点赞数量
     */
    private Integer liked;

    /**
     * 评论数量
     */
    private Integer comments;

    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 发布时挂载的商品 ID 列表，不直接映射到 tb_blog。
     */
    @TableField(exist = false)
    private List<Long> productIds;

    public String getContentType() {
        return ContentType.resolve(contentType, videoUrl);
    }

    public void setContentType(String contentType) {
        this.contentType = ContentType.normalizeName(contentType);
    }

}
