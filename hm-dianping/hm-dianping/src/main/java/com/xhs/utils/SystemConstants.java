package com.xhs.utils;

public class SystemConstants {
    public static final String IMAGE_UPLOAD_DIR = System.getenv().getOrDefault(
            "XHS_IMAGE_UPLOAD_DIR",
            "D:\\xiaohongshu\\nginx-1.18.0\\html\\xiaohongshu\\imgs"
    );
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
