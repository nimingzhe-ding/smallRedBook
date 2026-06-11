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
 * 商家主体。
 * 第一版采用“用户申请即开店”的轻量模式，后续可把 status 接入管理员审核。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_merchant")
public class Merchant implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String avatar;
    private String description;
    private String phone;
    private String address;
    /**
     * 1 正常营业，2 休息中，3 已关闭。
     */
    private Integer status;
    /**
     * 1 待审核，2 已通过，3 已拒绝。第一版自动通过。
     */
    private Integer auditStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
