package com.xhs.service;

public interface SeckillRedisRollbackService {

    void rollback(Long voucherId, Long userId);
}
