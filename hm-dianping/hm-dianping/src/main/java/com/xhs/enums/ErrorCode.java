package com.xhs.enums;

import lombok.Getter;

/**
 * 统一错误码枚举。
 * 前端可根据 code 值做国际化或差异化提示。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // ---- 业务错误 1001+ ----
    USER_NOT_LOGIN(1001, "请先登录"),
    PARAM_EMPTY(1002, "参数不能为空"),
    DATA_NOT_EXIST(1003, "数据不存在"),
    OPERATION_FAIL(1004, "操作失败"),
    STOCK_NOT_ENOUGH(1005, "库存不足"),
    REPEAT_OPERATION(1006, "不能重复操作"),
    SHOP_NOT_EXIST(1007, "店铺不存在"),
    BLOG_NOT_EXIST(1008, "笔记不存在"),
    COMMENT_NOT_EXIST(1009, "评论不存在"),
    NO_PERMISSION(1010, "无权操作"),
    VOUCHER_NOT_EXIST(1011, "优惠券不存在"),
    SECKILL_NOT_START(1012, "秒杀尚未开始"),
    SECKILL_ENDED(1013, "秒杀已经结束"),
    SERVER_BUSY(1014, "服务繁忙，请稍后重试"),
    PRODUCT_NOT_ONLINE(1015, "商品不存在或未上架"),
    NEED_MOUNT_PRODUCT(1016, "商品种草至少需要挂载一个商品"),
    TITLE_CONTENT_REQUIRED(1017, "标题和正文不能为空"),
    UPLOAD_FAIL(1018, "上传失败"),
    FILE_EMPTY(1019, "文件不能为空"),
    FOLLOW_SELF(1020, "不能关注自己"),
    ALREADY_FOLLOWED(1021, "已关注"),
    ;

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
