package com.xhs.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举。
 * 数值存入 tb_user.role 字段，层级递增。
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    GUEST(0, "游客"),
    USER(1, "普通用户"),
    MERCHANT(2, "商家"),
    ADMIN(3, "管理员");

    private final int code;
    private final String desc;

    public static UserRole of(int code) {
        for (UserRole role : values()) {
            if (role.code == code) return role;
        }
        return GUEST;
    }

    /**
     * 判断当前角色是否满足最低要求。
     */
    public boolean isAtLeast(UserRole required) {
        return this.code >= required.code;
    }
}
