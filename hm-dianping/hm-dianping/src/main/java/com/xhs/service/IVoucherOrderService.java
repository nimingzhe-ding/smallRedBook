package com.xhs.service;

import com.xhs.dto.Result;
import com.xhs.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    boolean createVoucherOrder(VoucherOrder voucherOrder);
}
