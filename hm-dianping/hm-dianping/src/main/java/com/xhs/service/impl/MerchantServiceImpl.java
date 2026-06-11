package com.xhs.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.dto.MerchantDashboardDTO;
import com.xhs.dto.MerchantProductRequest;
import com.xhs.dto.MerchantRequest;
import com.xhs.dto.MerchantVoucherRequest;
import com.xhs.dto.Result;
import com.xhs.dto.UserDTO;
import com.xhs.entity.MallOrder;
import com.xhs.entity.MallProduct;
import com.xhs.entity.Merchant;
import com.xhs.entity.MerchantNotification;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.entity.Voucher;
import com.xhs.mapper.MerchantMapper;
import com.xhs.mapper.MerchantNotificationMapper;
import com.xhs.service.IMallOrderService;
import com.xhs.service.IMallProductService;
import com.xhs.service.IMerchantService;
import com.xhs.service.IUserNotificationService;
import com.xhs.service.IVoucherService;
import com.xhs.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 商家中心完整实现。
 * 覆盖商品管理、订单处理、优惠券管理、通知和销售看板。
 */
@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements IMerchantService {

    @Resource
    private IMallProductService productService;

    @Resource
    private IMallOrderService orderService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private MerchantNotificationMapper notificationMapper;

    @Resource
    private IUserNotificationService userNotificationService;

    // ==================== 店铺 ====================

    @Override
    public Result mine() {
        Merchant merchant = currentMerchant();
        return Result.ok(merchant);
    }

    @Override
    public Result apply(MerchantRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "店铺名称不能为空");
        }
        Merchant merchant = query().eq("user_id", user.getId()).one();
        if (merchant == null) {
            merchant = new Merchant()
                    .setUserId(user.getId())
                    .setStatus(1)
                    .setAuditStatus(2);
        }
        fillMerchant(merchant, request);
        saveOrUpdate(merchant);
        return Result.ok(merchant);
    }

    // ==================== 商品管理 ====================

    @Override
    @Transactional
    public Result saveProduct(MerchantProductRequest request) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        Result check = checkProduct(request);
        if (!Boolean.TRUE.equals(check.getSuccess())) {
            return check;
        }
        LocalDateTime now = LocalDateTime.now();
        MallProduct product = new MallProduct()
                .setMerchantId(merchant.getId())
                .setTitle(request.getTitle())
                .setSubTitle(request.getSubTitle())
                .setImages(request.getImages())
                .setPrice(request.getPrice())
                .setOriginPrice(request.getOriginPrice())
                .setStock(request.getStock())
                .setSold(0)
                .setCategory(StrUtil.blankToDefault(request.getCategory(), "all"))
                .setCategoryId(request.getCategoryId())
                .setSubCategoryId(request.getSubCategoryId())
                .setSpecSummary(request.getSpecSummary())
                .setScore(0)
                .setReviewCount(0)
                .setFavoriteCount(0)
                .setStatus(request.getStatus() == null ? 1 : request.getStatus())
                .setCreateTime(now)
                .setUpdateTime(now);
        productService.save(product);
        return Result.ok(product);
    }

    @Override
    @Transactional
    public Result updateProduct(Long productId, MerchantProductRequest request) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        MallProduct product = productService.getById(productId);
        if (product == null || !merchant.getId().equals(product.getMerchantId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商品不存在");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "商品参数不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isNotBlank(request.getTitle())) product.setTitle(request.getTitle());
        if (request.getSubTitle() != null) product.setSubTitle(request.getSubTitle());
        if (request.getImages() != null) product.setImages(request.getImages());
        if (request.getPrice() != null && request.getPrice() > 0) product.setPrice(request.getPrice());
        if (request.getOriginPrice() != null) product.setOriginPrice(request.getOriginPrice());
        if (request.getStock() != null && request.getStock() >= 0) product.setStock(request.getStock());
        if (StrUtil.isNotBlank(request.getCategory())) product.setCategory(request.getCategory());
        if (request.getCategoryId() != null) product.setCategoryId(request.getCategoryId());
        if (request.getSubCategoryId() != null) product.setSubCategoryId(request.getSubCategoryId());
        if (request.getSpecSummary() != null) product.setSpecSummary(request.getSpecSummary());
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        product.setUpdateTime(now);
        productService.updateById(product);
        return Result.ok(product);
    }

    @Override
    public Result updateProductStatus(Long productId, Integer status) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态值不合法，0=下架 1=上架");
        }
        MallProduct product = productService.getById(productId);
        if (product == null || !merchant.getId().equals(product.getMerchantId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商品不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = productService.update()
                .set("status", status)
                .set("update_time", now)
                .eq("id", productId)
                .eq("merchant_id", merchant.getId())
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL);
        }
        return Result.ok(status == 1 ? "已上架" : "已下架");
    }

    @Override
    public Result adjustStock(Long productId, Integer delta) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        if (delta == null || delta == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "调整数量不能为0");
        }
        MallProduct product = productService.getById(productId);
        if (product == null || !merchant.getId().equals(product.getMerchantId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商品不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = productService.update()
                .setSql("stock = GREATEST(IFNULL(stock, 0) + " + delta + ", 0)")
                .set("update_time", now)
                .eq("id", productId)
                .eq("merchant_id", merchant.getId())
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "调整库存失败");
        }
        return Result.ok("库存已调整");
    }

    @Override
    public Result myProducts(Integer status) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        LambdaQueryWrapper<MallProduct> wrapper = new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getMerchantId, merchant.getId())
                .orderByDesc(MallProduct::getCreateTime);
        if (status != null) {
            wrapper.eq(MallProduct::getStatus, status);
        }
        return Result.ok(productService.list(wrapper));
    }

    // ==================== 订单管理 ====================

    @Override
    public Result myOrders(Integer status) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMerchantId, merchant.getId())
                .orderByDesc(MallOrder::getCreateTime);
        if (status != null) {
            wrapper.eq(MallOrder::getStatus, status);
        }
        return Result.ok(orderService.list(wrapper));
    }

    @Override
    public Result shipOrder(Long orderId) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        MallOrder order = orderService.getById(orderId);
        if (order == null || !merchant.getId().equals(order.getMerchantId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = orderService.update()
                .set("status", MallOrderServiceImpl.STATUS_SHIPPED)
                .set("ship_time", now)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("merchant_id", merchant.getId())
                .eq("status", MallOrderServiceImpl.STATUS_PENDING_SHIP)
                .update();
        if (updated) {
            userNotificationService.notifyUser(order.getUserId(), null, "ORDER_SHIPPED", "商家已发货",
                    "你购买的「" + order.getProductTitle() + "」已发货。", null, order.getId());
        }
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "当前订单状态不能发货");
        }
        return Result.ok("已发货");
    }

    @Override
    @Transactional
    public Result handleRefund(Long orderId, boolean approve) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        MallOrder order = orderService.getById(orderId);
        if (order == null || !merchant.getId().equals(order.getMerchantId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        }
        if (order.getStatus() != MallOrderServiceImpl.STATUS_REFUNDING) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "该订单不在退款中");
        }
        LocalDateTime now = LocalDateTime.now();
        if (approve) {
            // 同意退款：回滚库存 + 改状态为已取消
            productService.update()
                    .setSql("stock = stock + " + order.getQuantity())
                    .setSql("sold = GREATEST(IFNULL(sold, 0) - " + order.getQuantity() + ", 0)")
                    .eq("id", order.getProductId())
                    .update();
            orderService.update()
                    .set("status", MallOrderServiceImpl.STATUS_CANCELLED)
                    .set("cancel_time", now)
                    .set("update_time", now)
                    .eq("id", orderId)
                    .eq("status", MallOrderServiceImpl.STATUS_REFUNDING)
                    .update();
            userNotificationService.notifyUser(order.getUserId(), null, "ORDER_REFUND_APPROVED", "退款已通过",
                    "你购买的「" + order.getProductTitle() + "」退款已通过，款项将原路返回。", null, order.getId());
            return Result.ok("已同意退款");
        } else {
            // 拒绝退款：回到已支付状态
            boolean updated = orderService.update()
                    .set("status", MallOrderServiceImpl.STATUS_PAID)
                    .set("update_time", now)
                    .eq("id", orderId)
                    .eq("status", MallOrderServiceImpl.STATUS_REFUNDING)
                    .update();
            if (!updated) {
                throw new BusinessException(ErrorCode.OPERATION_FAIL);
            }
            userNotificationService.notifyUser(order.getUserId(), null, "ORDER_REFUND_REJECTED", "退款被拒绝",
                    "你购买的「" + order.getProductTitle() + "」的退款申请已被商家拒绝。", null, order.getId());
            return Result.ok("已拒绝退款");
        }
    }

    // ==================== 优惠券管理 ====================

    @Override
    public Result createVoucher(MerchantVoucherRequest request) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        Result check = checkVoucher(request);
        if (!Boolean.TRUE.equals(check.getSuccess())) {
            return check;
        }
        if (request.getProductId() != null) {
            MallProduct product = productService.getById(request.getProductId());
            if (product == null || !merchant.getId().equals(product.getMerchantId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "只能给自己的商品发券");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        Voucher voucher = new Voucher()
                .setMerchantId(merchant.getId())
                .setProductId(request.getProductId())
                .setScopeType(StrUtil.blankToDefault(request.getScopeType(), request.getProductId() == null ? "SHOP" : "PRODUCT"))
                .setCategoryId(request.getCategoryId())
                .setPlatformId(request.getPlatformId())
                .setTitle(request.getTitle())
                .setSubTitle(request.getSubTitle())
                .setRules(request.getRules())
                .setPayValue(request.getPayValue())
                .setActualValue(request.getActualValue())
                .setType(0)
                .setStatus(1)
                .setCreateTime(now)
                .setUpdateTime(now);
        voucherService.save(voucher);
        return Result.ok(voucher);
    }

    @Override
    public Result updateVoucher(Long voucherId, MerchantVoucherRequest request) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || !merchant.getId().equals(voucher.getMerchantId())) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_EXIST);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "优惠券参数不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        if (StrUtil.isNotBlank(request.getTitle())) voucher.setTitle(request.getTitle());
        if (request.getSubTitle() != null) voucher.setSubTitle(request.getSubTitle());
        if (request.getRules() != null) voucher.setRules(request.getRules());
        if (request.getScopeType() != null) voucher.setScopeType(request.getScopeType());
        if (request.getCategoryId() != null) voucher.setCategoryId(request.getCategoryId());
        if (request.getPlatformId() != null) voucher.setPlatformId(request.getPlatformId());
        if (request.getPayValue() != null) voucher.setPayValue(request.getPayValue());
        if (request.getActualValue() != null) voucher.setActualValue(request.getActualValue());
        voucher.setUpdateTime(now);
        voucherService.updateById(voucher);
        return Result.ok(voucher);
    }

    @Override
    public Result updateVoucherStatus(Long voucherId, Integer status) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态值不合法，0=下架 1=上架");
        }
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || !merchant.getId().equals(voucher.getMerchantId())) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_EXIST);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = voucherService.update()
                .set("status", status)
                .set("update_time", now)
                .eq("id", voucherId)
                .eq("merchant_id", merchant.getId())
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL);
        }
        return Result.ok(status == 1 ? "已上架" : "已下架");
    }

    @Override
    public Result myVouchers(Integer status) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        LambdaQueryWrapper<Voucher> wrapper = new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getMerchantId, merchant.getId())
                .orderByDesc(Voucher::getCreateTime);
        if (status != null) {
            wrapper.eq(Voucher::getStatus, status);
        }
        return Result.ok(voucherService.list(wrapper));
    }

    // ==================== 通知 ====================

    @Override
    public Result notifications(Integer readFlag) {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        LambdaQueryWrapper<MerchantNotification> wrapper = new LambdaQueryWrapper<MerchantNotification>()
                .eq(MerchantNotification::getMerchantId, merchant.getId())
                .orderByDesc(MerchantNotification::getCreateTime);
        if (readFlag != null) {
            wrapper.eq(MerchantNotification::getReadFlag, readFlag == 1);
        }
        return Result.ok(notificationMapper.selectList(wrapper));
    }

    @Override
    public Result markNotificationsRead() {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        notificationMapper.update(
                new MerchantNotification().setReadFlag(true),
                new LambdaQueryWrapper<MerchantNotification>()
                        .eq(MerchantNotification::getMerchantId, merchant.getId())
                        .eq(MerchantNotification::getReadFlag, false)
        );
        return Result.ok();
    }

    // ==================== 销售看板 ====================

    @Override
    public Result dashboard() {
        Merchant merchant = currentMerchant();
        if (merchant == null) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "请先开通商家中心");
        }
        Long merchantId = merchant.getId();
        MerchantDashboardDTO dto = new MerchantDashboardDTO();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        // 今日订单数
        dto.setTodayOrders(orderService.count(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMerchantId, merchantId)
                .ge(MallOrder::getCreateTime, todayStart)
                .le(MallOrder::getCreateTime, todayEnd)));

        // 今日已支付订单数
        dto.setTodayPaidOrders(orderService.count(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMerchantId, merchantId)
                .in(MallOrder::getStatus,
                        MallOrderServiceImpl.STATUS_PAID,
                        MallOrderServiceImpl.STATUS_PENDING_SHIP,
                        MallOrderServiceImpl.STATUS_SHIPPED,
                        MallOrderServiceImpl.STATUS_COMPLETED)
                .ge(MallOrder::getCreateTime, todayStart)
                .le(MallOrder::getCreateTime, todayEnd)));

        // 今日销售额（已支付的订单 totalAmount 之和）
        List<MallOrder> todayPaidOrders = orderService.list(new LambdaQueryWrapper<MallOrder>()
                .select(MallOrder::getTotalAmount)
                .eq(MallOrder::getMerchantId, merchantId)
                .in(MallOrder::getStatus,
                        MallOrderServiceImpl.STATUS_PAID,
                        MallOrderServiceImpl.STATUS_PENDING_SHIP,
                        MallOrderServiceImpl.STATUS_SHIPPED,
                        MallOrderServiceImpl.STATUS_COMPLETED)
                .ge(MallOrder::getCreateTime, todayStart)
                .le(MallOrder::getCreateTime, todayEnd));
        dto.setTodaySalesAmount(todayPaidOrders.stream()
                .mapToLong(o -> o.getTotalAmount() == null ? 0L : o.getTotalAmount())
                .sum());

        // 总销售额
        List<MallOrder> allPaidOrders = orderService.list(new LambdaQueryWrapper<MallOrder>()
                .select(MallOrder::getTotalAmount)
                .eq(MallOrder::getMerchantId, merchantId)
                .in(MallOrder::getStatus,
                        MallOrderServiceImpl.STATUS_PAID,
                        MallOrderServiceImpl.STATUS_PENDING_SHIP,
                        MallOrderServiceImpl.STATUS_SHIPPED,
                        MallOrderServiceImpl.STATUS_COMPLETED));
        dto.setTotalSalesAmount(allPaidOrders.stream()
                .mapToLong(o -> o.getTotalAmount() == null ? 0L : o.getTotalAmount())
                .sum());

        // 待发货数
        dto.setPendingShipCount(orderService.count(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMerchantId, merchantId)
                .eq(MallOrder::getStatus, MallOrderServiceImpl.STATUS_PENDING_SHIP)));

        // 待退款数
        dto.setPendingRefundCount(orderService.count(new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getMerchantId, merchantId)
                .eq(MallOrder::getStatus, MallOrderServiceImpl.STATUS_REFUNDING)));

        // 商品统计
        dto.setTotalProducts(productService.count(new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getMerchantId, merchantId)));
        dto.setOnSaleProducts(productService.count(new LambdaQueryWrapper<MallProduct>()
                .eq(MallProduct::getMerchantId, merchantId)
                .eq(MallProduct::getStatus, 1)));

        // 优惠券统计
        dto.setTotalVouchers(voucherService.count(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getMerchantId, merchantId)));
        dto.setActiveVouchers(voucherService.count(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getMerchantId, merchantId)
                .eq(Voucher::getStatus, 1)));

        // 未读通知数
        dto.setUnreadNotifications(notificationMapper.selectCount(new LambdaQueryWrapper<MerchantNotification>()
                .eq(MerchantNotification::getMerchantId, merchantId)
                .eq(MerchantNotification::getReadFlag, false)));

        return Result.ok(dto);
    }

    // ==================== 私有方法 ====================

    private Result checkProduct(MerchantProductRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "商品参数不能为空");
        }
        if (StrUtil.isBlank(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "商品标题不能为空");
        }
        if (request.getPrice() == null || request.getPrice() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品价格必须大于0");
        }
        if (request.getStock() == null || request.getStock() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库存不能小于0");
        }
        return Result.ok();
    }

    private Result checkVoucher(MerchantVoucherRequest request) {
        if (request == null || StrUtil.isBlank(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "优惠券标题不能为空");
        }
        if (request.getPayValue() == null || request.getPayValue() < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "优惠金额不正确");
        }
        if (request.getActualValue() == null || request.getActualValue() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "抵扣金额必须大于0");
        }
        return Result.ok();
    }

    private Merchant currentMerchant() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return null;
        }
        return query().eq("user_id", user.getId()).one();
    }

    private void fillMerchant(Merchant merchant, MerchantRequest request) {
        merchant.setName(request.getName());
        merchant.setAvatar(request.getAvatar());
        merchant.setDescription(request.getDescription());
        merchant.setPhone(request.getPhone());
        merchant.setAddress(request.getAddress());
    }
}
