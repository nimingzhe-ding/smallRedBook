package com.xhs.enums;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;

/**
 * 内容种草交易场景下的笔记类型体系。
 */
public enum ContentType {
    IMAGE,
    VIDEO,
    PRODUCT_NOTE;

    public static boolean isSupported(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        return Arrays.stream(values()).anyMatch(type -> type.name().equalsIgnoreCase(value.trim()));
    }

    public static String normalizeName(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        return isSupported(normalized) ? normalized : null;
    }

    public static String resolve(String value, String videoUrl) {
        String normalized = normalizeName(value);
        if (normalized != null) {
            return normalized;
        }
        return StrUtil.isNotBlank(videoUrl) ? VIDEO.name() : IMAGE.name();
    }

    public static boolean supportsDanmaku(String value, String videoUrl) {
        String resolved = resolve(value, videoUrl);
        return VIDEO.name().equals(resolved);
    }
}
