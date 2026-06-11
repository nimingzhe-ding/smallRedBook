package com.hmdp.controller;


import com.hmdp.annotation.Idempotent;
import com.hmdp.annotation.SlidingWindowRateLimit;
import com.hmdp.dto.Result;
import com.hmdp.enums.RateLimitScope;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @PostMapping("seckill/{id}")
    @SlidingWindowRateLimit(key = "voucher-order:seckill", maxRequests = 5, windowSeconds = 10, scope = RateLimitScope.USER_OR_IP)
    @Idempotent(key = "voucher-order:seckill", expireSeconds = 30)
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
