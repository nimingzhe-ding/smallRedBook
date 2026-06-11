package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.compensation.CompensationEventTypes;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.CompensationEventService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.SeckillRedisRollbackService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private CompensationEventService compensationEventService;
    @Resource
    private SeckillRedisRollbackService seckillRedisRollbackService;

    @Value("${hmdp.seckill.kafka.order-topic:seckill-order}")
    private String orderTopic;

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (voucherId == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "Voucher id cannot be empty");
        }

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_EXIST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime() != null && voucher.getBeginTime().isAfter(now)) {
            throw new BusinessException(ErrorCode.SECKILL_NOT_START);
        }
        if (voucher.getEndTime() != null && voucher.getEndTime().isBefore(now)) {
            throw new BusinessException(ErrorCode.SECKILL_ENDED);
        }

        Long userId = user.getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        if (result == null) {
            throw new BusinessException(ErrorCode.SERVER_BUSY);
        }

        int code = result.intValue();
        if (code != 0) {
            if (code == 1) {
                throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
            }
            throw new BusinessException(ErrorCode.REPEAT_OPERATION, "Duplicate seckill order");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        try {
            sendSeckillOrder(voucherOrder);
        } catch (Exception e) {
            rollbackSeckill(voucherId, userId);
            log.error("Send seckill order Kafka message failed, orderId={}, userId={}, voucherId={}",
                    orderId, userId, voucherId, e);
            throw new BusinessException(ErrorCode.SERVER_BUSY, "Order message send failed, please retry later");
        }

        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public boolean createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.warn("Duplicate voucher order, userId={}, voucherId={}", userId, voucherId);
            return false;
        }

        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            log.warn("Voucher stock update failed, userId={}, voucherId={}", userId, voucherId);
            return false;
        }

        try {
            return save(voucherOrder);
        } catch (DuplicateKeyException e) {
            log.warn("Voucher order unique key conflict, userId={}, voucherId={}", userId, voucherId);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${hmdp.seckill.kafka.order-topic:seckill-order}",
            groupId = "${hmdp.seckill.kafka.order-consumer-group:seckill-order-consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSeckillOrderMessage(String payload, Acknowledgment acknowledgment) throws Exception {
        VoucherOrder voucherOrder = objectMapper.readValue(payload, VoucherOrder.class);
        handleVoucherOrder(voucherOrder);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${hmdp.seckill.kafka.order-dlt-topic:seckill-order.DLT}",
            groupId = "seckill-order-dlt-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleSeckillOrderDltMessage(String payload, Acknowledgment acknowledgment) {
        try {
            VoucherOrder voucherOrder = objectMapper.readValue(payload, VoucherOrder.class);
            rollbackSeckill(voucherOrder.getVoucherId(), voucherOrder.getUserId());
            log.error("Seckill order entered DLT, Redis pre-deduction rollback requested. orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId());
        } catch (Exception e) {
            log.error("Handle seckill order DLT message failed, payload={}", payload, e);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private void sendSeckillOrder(VoucherOrder voucherOrder) throws Exception {
        String payload = objectMapper.writeValueAsString(voucherOrder);
        kafkaTemplate.send(orderTopic, voucherOrder.getVoucherId().toString(), payload).get(3, TimeUnit.SECONDS);
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            log.warn("Duplicate voucher order lock rejected, userId={}, voucherId={}", userId, voucherOrder.getVoucherId());
            rollbackSeckill(voucherOrder.getVoucherId(), userId);
            return;
        }

        try {
            Boolean created = transactionTemplate.execute(status -> createVoucherOrder(voucherOrder));
            if (!Boolean.TRUE.equals(created)) {
                rollbackSeckill(voucherOrder.getVoucherId(), userId);
            }
        } catch (DuplicateKeyException e) {
            rollbackSeckill(voucherOrder.getVoucherId(), userId);
            log.warn("Voucher order unique key conflict, Redis rollback requested. orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), userId, voucherOrder.getVoucherId());
        } catch (Exception e) {
            log.error("Handle Kafka seckill order failed, waiting for Kafka retry. orderId={}, userId={}, voucherId={}",
                    voucherOrder.getId(), userId, voucherOrder.getVoucherId(), e);
            throw e;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void rollbackSeckill(Long voucherId, Long userId) {
        try {
            seckillRedisRollbackService.rollback(voucherId, userId);
        } catch (Exception e) {
            log.error("Rollback seckill Redis state failed, voucherId={}, userId={}", voucherId, userId, e);
            compensationEventService.record(
                    CompensationEventTypes.SECKILL_REDIS_ROLLBACK,
                    "SECKILL",
                    voucherId + ":" + userId,
                    "seckill:redis-rollback:" + voucherId + ":" + userId,
                    Map.of("voucherId", voucherId, "userId", userId),
                    e.getMessage()
            );
        }
    }
}
