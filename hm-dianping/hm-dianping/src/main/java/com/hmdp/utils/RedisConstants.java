package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_CODE_COOLDOWN_KEY = "login:code:cooldown:";
    public static final Long LOGIN_CODE_COOLDOWN_SECONDS = 60L;
    public static final String LOGIN_FAIL_KEY = "login:fail:";
    public static final Long LOGIN_FAIL_TTL = 10L;
    public static final Long LOGIN_FAIL_LOCK_SECONDS = 300L;
    public static final Long LOGIN_FAIL_MAX = 5L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_VIDEO_KEY = "cache:video:";
    public static final String CACHE_USER_KEY = "cache:user:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";

    // 用户兴趣画像（个性化推荐）
    public static final String USER_INTEREST_KEY = "user:interest:";
    public static final Long USER_INTEREST_TTL = 7L;

    // 搜索历史
    public static final String SEARCH_HISTORY_KEY = "search:history:";
    public static final Long SEARCH_HISTORY_MAX = 20L;
    public static final Long SEARCH_HISTORY_TTL = 90L;

    // 全局店铺GEO索引（附近流）
    public static final String SHOP_GEO_ALL_KEY = "shop:geo:all";

    // 热搜榜缓存
    public static final String HOT_SEARCH_KEY = "hot:search";
    public static final Long HOT_SEARCH_TTL = 15L;

    // 笔记行为事件流
    public static final String NOTE_EVENT_STREAM_KEY = "stream.notes.events";
    public static final String VIDEO_DANMAKU_ZSET_KEY = "danmaku:video:";
}
