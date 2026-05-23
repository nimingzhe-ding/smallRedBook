package com.hmdp.controller;

import com.hmdp.dto.MallCartRequest;
import com.hmdp.dto.MallOrderRequest;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.ErrorCode;
import com.hmdp.service.IMallCartService;
import com.hmdp.service.IMallOrderService;
import com.hmdp.service.IMallProductService;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商城统一接口。
 * 第一版提供商品流、商品详情、购物车和下单能力，用于把原本的店铺交易扩展成电商购物。
 */
@RestController
@RequestMapping("/mall")
public class MallController {

    @Resource
    private IMallProductService productService;

    @Resource
    private IMallCartService cartService;

    @Resource
    private IMallOrderService orderService;

    @GetMapping("/products")
    public Result products(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return productService.pageProducts(category, query, current);
    }

    @GetMapping("/products/{id}")
    public Result detail(@PathVariable("id") Long id) {
        return productService.detail(id);
    }

    @PostMapping("/cart")
    public Result addCart(@RequestBody MallCartRequest request) {
        return cartService.add(request);
    }

    @GetMapping("/cart")
    public Result cart() {
        return cartService.listMine();
    }

    @DeleteMapping("/cart/{id}")
    public Result removeCartItem(@PathVariable("id") Long id) {
        return cartService.removeItem(id);
    }

    @PostMapping("/orders")
    public Result createOrder(@RequestBody MallOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/orders")
    public Result orders(@RequestParam(value = "status", required = false) Integer status) {
        return orderService.listMine(status);
    }

    @GetMapping("/orders/summary")
    public Result orderSummary() {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail(ErrorCode.USER_NOT_LOGIN);
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("all", orderService.query().eq("user_id", user.getId()).count());
        summary.put("pendingPay", orderService.query().eq("user_id", user.getId()).eq("status", 1).count());
        summary.put("pendingShip", orderService.query().eq("user_id", user.getId()).in("status", 2, 3).count());
        summary.put("shipped", orderService.query().eq("user_id", user.getId()).eq("status", 4).count());
        summary.put("completed", orderService.query().eq("user_id", user.getId()).eq("status", 5).count());
        summary.put("cancelled", orderService.query().eq("user_id", user.getId()).eq("status", 6).count());
        summary.put("refund", orderService.query().eq("user_id", user.getId()).in("status", 7, 8).count());
        return Result.ok(summary);
    }

    @PostMapping("/orders/{id}/pay")
    public Result payOrder(@PathVariable("id") Long id) {
        return orderService.payOrder(id);
    }

    @PostMapping("/orders/{id}/ship")
    public Result shipOrder(@PathVariable("id") Long id) {
        return orderService.shipOrder(id);
    }

    @PostMapping("/orders/{id}/receive")
    public Result receiveOrder(@PathVariable("id") Long id) {
        return orderService.receiveOrder(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public Result cancelOrder(@PathVariable("id") Long id) {
        return orderService.cancelOrder(id);
    }

    @PostMapping("/orders/{id}/refund")
    public Result refundOrder(@PathVariable("id") Long id) {
        return orderService.refundOrder(id);
    }
}
