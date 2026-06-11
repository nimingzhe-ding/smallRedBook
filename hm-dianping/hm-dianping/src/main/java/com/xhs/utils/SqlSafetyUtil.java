package com.xhs.utils;

import java.util.List;

/**
 * SQL 安全工具：防止 order by field 等动态 SQL 拼接注入。
 */
public final class SqlSafetyUtil {

    private SqlSafetyUtil() {}

    /**
     * 安全地拼接 order by field(id, ...) 子句。
     * 校验所有 ID 必须为正整数，否则拒绝拼接。
     *
     * @param ids ID 列表
     * @return 拼接好的 "order by field(id,1,2,3)" 字符串
     * @throws IllegalArgumentException 如果包含非法 ID
     */
    public static String orderByField(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("非法ID: " + id);
            }
        }
        return "order by field(id," + cn.hutool.core.util.StrUtil.join(",", ids) + ")";
    }

    /**
     * 安全地拼接 IN (...) 子句中的 ID 列表。
     * 校验所有 ID 必须为正整数。
     */
    public static String inClause(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "(NULL)";
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("非法ID: " + id);
            }
        }
        return "(" + cn.hutool.core.util.StrUtil.join(",", ids) + ")";
    }
}
