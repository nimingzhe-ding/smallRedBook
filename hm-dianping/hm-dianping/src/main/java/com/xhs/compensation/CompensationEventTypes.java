package com.xhs.compensation;

public final class CompensationEventTypes {

    public static final String DANMAKU_KAFKA_PERSIST = "DANMAKU_KAFKA_PERSIST";
    public static final String DANMAKU_REDIS_ZSET_REFRESH = "DANMAKU_REDIS_ZSET_REFRESH";
    public static final String REDIS_VIDEO_CACHE_REFRESH = "REDIS_VIDEO_CACHE_REFRESH";
    public static final String REDIS_VIDEO_CACHE_EVICT = "REDIS_VIDEO_CACHE_EVICT";
    public static final String REDIS_USER_CACHE_REFRESH = "REDIS_USER_CACHE_REFRESH";
    public static final String REDIS_USER_CACHE_EVICT = "REDIS_USER_CACHE_EVICT";
    public static final String SEARCH_INDEX_BLOG_UPSERT = "SEARCH_INDEX_BLOG_UPSERT";
    public static final String SEARCH_INDEX_BLOG_DELETE = "SEARCH_INDEX_BLOG_DELETE";
    public static final String SEARCH_INDEX_USER_UPSERT = "SEARCH_INDEX_USER_UPSERT";
    public static final String SEARCH_INDEX_USER_DELETE = "SEARCH_INDEX_USER_DELETE";
    public static final String SEARCH_INDEX_DANMAKU_UPSERT = "SEARCH_INDEX_DANMAKU_UPSERT";
    public static final String SEARCH_INDEX_DANMAKU_DELETE = "SEARCH_INDEX_DANMAKU_DELETE";
    public static final String OSS_MULTIPART_ABORT = "OSS_MULTIPART_ABORT";
    public static final String SECKILL_REDIS_ROLLBACK = "SECKILL_REDIS_ROLLBACK";
    public static final String PRIVATE_MESSAGE_WS_PUSH = "PRIVATE_MESSAGE_WS_PUSH";

    private CompensationEventTypes() {
    }
}
