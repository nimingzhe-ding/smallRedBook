package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.MallOrderRequest;
import com.xhs.dto.Result;
import com.xhs.entity.MallOrder;

/**
 * 商城订单服务。
 */
public interface IMallOrderService extends IService<MallOrder> {
    Result createOrder(MallOrderRequest request);

    Result listMine(Integer status);

    Result payOrder(Long orderId);

    Result shipOrder(Long orderId);

    Result receiveOrder(Long orderId);

    Result cancelOrder(Long orderId);

    Result refundOrder(Long orderId);
}
