package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.MerchantProductRequest;
import com.xhs.dto.MerchantRequest;
import com.xhs.dto.MerchantVoucherRequest;
import com.xhs.dto.Result;
import com.xhs.entity.Merchant;

/**
 * 商家中心服务。
 */
public interface IMerchantService extends IService<Merchant> {
    Result mine();

    Result apply(MerchantRequest request);

    Result saveProduct(MerchantProductRequest request);

    Result updateProduct(Long productId, MerchantProductRequest request);

    Result updateProductStatus(Long productId, Integer status);

    Result adjustStock(Long productId, Integer delta);

    Result myProducts(Integer status);

    Result myOrders(Integer status);

    Result shipOrder(Long orderId);

    Result handleRefund(Long orderId, boolean approve);

    Result createVoucher(MerchantVoucherRequest request);

    Result updateVoucher(Long voucherId, MerchantVoucherRequest request);

    Result updateVoucherStatus(Long voucherId, Integer status);

    Result myVouchers(Integer status);

    Result notifications(Integer readFlag);

    Result markNotificationsRead();

    Result dashboard();
}
