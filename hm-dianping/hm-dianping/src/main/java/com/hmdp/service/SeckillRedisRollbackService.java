package com.hmdp.service;

public interface SeckillRedisRollbackService {

    void rollback(Long voucherId, Long userId);
}
