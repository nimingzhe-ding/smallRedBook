package com.hmdp.enums;

/**
 * 用户行为事件类型。
 * 用于 NoteEvent.eventType 字段，提供编译期安全。
 */
public enum EventType {
    IMPRESSION,
    CLICK,
    SEARCH,
    DETAIL,
    DWELL,
    LIKE,
    COLLECT,
    COMMENT,
    PLAY,
    SHARE,
    UNCOLLECT,
    PURCHASE
}
