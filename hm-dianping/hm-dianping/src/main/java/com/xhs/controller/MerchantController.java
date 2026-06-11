package com.xhs.controller;

import com.xhs.annotation.RequireRole;
import com.xhs.dto.MerchantProductRequest;
import com.xhs.dto.MerchantRequest;
import com.xhs.dto.MerchantVoucherRequest;
import com.xhs.dto.Result;
import com.xhs.enums.UserRole;
import com.xhs.service.IMerchantService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家中心接口。
 */
@RestController
@RequestMapping("/merchant")
@RequireRole(UserRole.USER)
public class MerchantController {

    @Resource
    private IMerchantService merchantService;

    // ========== 店铺 ==========

    @GetMapping("/mine")
    public Result mine() {
        return merchantService.mine();
    }

    @PostMapping("/apply")
    public Result apply(@RequestBody MerchantRequest request) {
        return merchantService.apply(request);
    }

    // ========== 看板 ==========

    @GetMapping("/dashboard")
    public Result dashboard() {
        return merchantService.dashboard();
    }

    // ========== 商品管理 ==========

    @GetMapping("/products")
    public Result products(@RequestParam(value = "status", required = false) Integer status) {
        return merchantService.myProducts(status);
    }

    @PostMapping("/products")
    public Result saveProduct(@RequestBody MerchantProductRequest request) {
        return merchantService.saveProduct(request);
    }

    @PutMapping("/products/{id}")
    public Result updateProduct(@PathVariable("id") Long id, @RequestBody MerchantProductRequest request) {
        return merchantService.updateProduct(id, request);
    }

    @PostMapping("/products/{id}/status")
    public Result updateProductStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        return merchantService.updateProductStatus(id, status);
    }

    @PostMapping("/products/{id}/stock")
    public Result adjustStock(@PathVariable("id") Long id, @RequestParam("delta") Integer delta) {
        return merchantService.adjustStock(id, delta);
    }

    // ========== 订单管理 ==========

    @GetMapping("/orders")
    public Result orders(@RequestParam(value = "status", required = false) Integer status) {
        return merchantService.myOrders(status);
    }

    @PostMapping("/orders/{id}/ship")
    public Result ship(@PathVariable("id") Long id) {
        return merchantService.shipOrder(id);
    }

    @PostMapping("/orders/{id}/refund")
    public Result handleRefund(@PathVariable("id") Long id, @RequestParam("approve") Boolean approve) {
        return merchantService.handleRefund(id, approve);
    }

    // ========== 优惠券管理 ==========

    @GetMapping("/vouchers")
    public Result vouchers(@RequestParam(value = "status", required = false) Integer status) {
        return merchantService.myVouchers(status);
    }

    @PostMapping("/vouchers")
    public Result createVoucher(@RequestBody MerchantVoucherRequest request) {
        return merchantService.createVoucher(request);
    }

    @PutMapping("/vouchers/{id}")
    public Result updateVoucher(@PathVariable("id") Long id, @RequestBody MerchantVoucherRequest request) {
        return merchantService.updateVoucher(id, request);
    }

    @PostMapping("/vouchers/{id}/status")
    public Result updateVoucherStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status) {
        return merchantService.updateVoucherStatus(id, status);
    }

    // ========== 通知 ==========

    @GetMapping("/notifications")
    public Result notifications(@RequestParam(value = "readFlag", required = false) Integer readFlag) {
        return merchantService.notifications(readFlag);
    }

    @PostMapping("/notifications/read")
    public Result markNotificationsRead() {
        return merchantService.markNotificationsRead();
    }
}
