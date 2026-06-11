package com.xhs.dto;

import lombok.Data;

/**
 * 商家后台首页数据看板。
 */
@Data
public class MerchantDashboardDTO {
    private long todayOrders;
    private long todayPaidOrders;
    private long todaySalesAmount;
    private long totalSalesAmount;
    private long pendingShipCount;
    private long pendingRefundCount;
    private long totalProducts;
    private long onSaleProducts;
    private long totalVouchers;
    private long activeVouchers;
    private long unreadNotifications;
}
