package com.xhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.dto.MallOrderRequest;
import com.xhs.dto.Result;
import com.xhs.dto.UserDTO;
import com.xhs.entity.MallCartItem;
import com.xhs.entity.MallLogistics;
import com.xhs.entity.MallOrder;
import com.xhs.entity.MallProduct;
import com.xhs.entity.MallRefund;
import com.xhs.entity.MallSku;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.entity.MerchantNotification;
import com.xhs.entity.UserAddress;
import com.xhs.entity.Voucher;
import com.xhs.enums.EventType;
import com.xhs.mapper.MallLogisticsMapper;
import com.xhs.mapper.MallOrderMapper;
import com.xhs.mapper.MallRefundMapper;
import com.xhs.mapper.MallSkuMapper;
import com.xhs.mapper.MerchantNotificationMapper;
import com.xhs.mapper.UserAddressMapper;
import com.xhs.service.IMallCartService;
import com.xhs.service.IMallOrderService;
import com.xhs.service.IMallProductService;
import com.xhs.service.INoteEventService;
import com.xhs.service.IUserNotificationService;
import com.xhs.service.IVoucherService;
import com.xhs.utils.RedisIdWorker;
import com.xhs.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商城订单服务实现。
 * 订单保存商品、SKU、地址、优惠和物流快照，避免后续商品资料变化影响历史订单。
 */
@Slf4j
@Service
public class MallOrderServiceImpl extends ServiceImpl<MallOrderMapper, MallOrder> implements IMallOrderService {

    public static final int STATUS_PENDING_PAY = 1;
    public static final int STATUS_PAID = 2;
    public static final int STATUS_PENDING_SHIP = 3;
    public static final int STATUS_SHIPPED = 4;
    public static final int STATUS_COMPLETED = 5;
    public static final int STATUS_CANCELLED = 6;
    public static final int STATUS_REFUNDING = 7;
    public static final int STATUS_REFUNDED = 8;

    @Resource
    private IMallProductService productService;
    @Resource
    private IMallCartService cartService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private MerchantNotificationMapper notificationMapper;
    @Resource
    private IUserNotificationService userNotificationService;
    @Resource
    private MallSkuMapper skuMapper;
    @Resource
    private UserAddressMapper addressMapper;
    @Resource
    private MallLogisticsMapper logisticsMapper;
    @Resource
    private MallRefundMapper refundMapper;
    @Resource
    private INoteEventService noteEventService;

    @Override
    @Transactional
    public Result createOrder(MallOrderRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        if (request == null) throw new BusinessException(ErrorCode.PARAM_EMPTY, "下单参数不能为空");

        MallCartItem cartItem = null;
        Long productId = request.getProductId();
        int quantity = request.getQuantity() == null || request.getQuantity() < 1 ? 1 : request.getQuantity();
        if (request.getCartItemId() != null) {
            cartItem = cartService.getById(request.getCartItemId());
            if (cartItem == null || !user.getId().equals(cartItem.getUserId())) {
                throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "购物车商品不存在");
            }
            productId = cartItem.getProductId();
            quantity = cartItem.getQuantity();
        }
        if (productId == null) throw new BusinessException(ErrorCode.PARAM_EMPTY, "商品ID不能为空");

        MallProduct product = productService.getById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ONLINE);
        }
        MallSku sku;
        try {
            sku = loadSku(request.getSkuId(), productId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
        int stock = sku == null ? safeInt(product.getStock()) : safeInt(sku.getStock());
        if (stock < quantity) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }

        UserAddress address = loadOrderAddress(request.getAddressId(), user.getId());
        if (address == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "请先选择收货地址");
        }

        long unitPrice = sku == null || sku.getPrice() == null ? safeLong(product.getPrice()) : sku.getPrice();
        long originalAmount = unitPrice * quantity;
        Voucher voucher;
        try {
            voucher = Boolean.TRUE.equals(request.getAutoBestCoupon())
                    ? chooseBestVoucher(product, originalAmount)
                    : resolveVoucher(request.getVoucherId(), product, originalAmount);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
        long discountAmount = voucher == null ? 0L : Math.min(safeLong(voucher.getActualValue()), originalAmount);
        LocalDateTime now = LocalDateTime.now();

        MallOrder order = new MallOrder();
        order.setId(redisIdWorker.nextId("mall_order"));
        order.setUserId(user.getId());
        order.setMerchantId(product.getMerchantId());
        order.setProductId(productId);
        order.setSkuId(sku == null ? null : sku.getId());
        order.setAddressId(address.getId());
        order.setVoucherId(voucher == null ? null : voucher.getId());
        order.setProductTitle(product.getTitle());
        order.setProductImage(firstImage(sku == null || sku.getImage() == null ? product.getImages() : sku.getImage()));
        order.setSkuName(sku == null ? null : sku.getSkuName());
        order.setSkuSpecs(sku == null ? null : sku.getSpecs());
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverAddress(formatAddress(address));
        order.setPrice(unitPrice);
        order.setQuantity(quantity);
        order.setDiscountAmount(discountAmount);
        order.setPromotionDiscountAmount(0L);
        order.setTotalAmount(Math.max(0, originalAmount - discountAmount));
        order.setStatus(STATUS_PENDING_PAY);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        save(order);

        noteEventService.track(user.getId(), null, EventType.PURCHASE, null, null);
        userNotificationService.notifyUser(user.getId(), null, "ORDER_CREATED", "订单已创建",
                "你购买的《" + order.getProductTitle() + "》已生成订单，等待支付。", null, order.getId());
        notifyMerchant(order.getMerchantId(), "order_created", order);
        if (cartItem != null) {
            cartService.removeById(cartItem.getId());
        }
        return Result.ok(order);
    }

    @Override
    public Result listMine(Integer status) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getUserId, user.getId())
                .orderByDesc(MallOrder::getCreateTime);
        if (status != null) {
            wrapper.eq(MallOrder::getStatus, status);
        }
        return Result.ok(list(wrapper));
    }

    @Override
    @Transactional
    public Result payOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = getOrderAndCheckOwner(orderId, user.getId());
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");

        boolean stockUpdated = decreaseStock(order);
        if (!stockUpdated) throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH, "库存不足，支付失败");

        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_PENDING_SHIP)
                .set("pay_time", now)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("user_id", user.getId())
                .eq("status", STATUS_PENDING_PAY)
                .update();
        if (!updated) {
            rollbackStock(order);
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "当前订单状态不能支付");
        }
        notifyMerchant(order.getMerchantId(), "order_paid", order);
        userNotificationService.notifyUser(order.getUserId(), null, "ORDER_PAID", "订单已支付",
                "你购买的《" + order.getProductTitle() + "》已支付成功，等待商家发货。", null, order.getId());
        return Result.ok(loadOrder(orderId));
    }

    @Override
    public Result shipOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = getById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");

        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_SHIPPED)
                .set("ship_time", now)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("status", STATUS_PENDING_SHIP)
                .update();
        if (!updated) throw new BusinessException(ErrorCode.OPERATION_FAIL, "当前订单状态不能发货");
        saveLogistics(order, null, null, "SHIPPED", now, null);
        userNotificationService.notifyUser(order.getUserId(), null, "ORDER_SHIPPED", "商家已发货",
                "你购买的《" + order.getProductTitle() + "》已发货。", null, order.getId());
        return Result.ok(loadOrder(orderId));
    }

    @Override
    public Result receiveOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);

        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_COMPLETED)
                .set("receive_time", now)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("user_id", user.getId())
                .eq("status", STATUS_SHIPPED)
                .update();
        if (!updated) throw new BusinessException(ErrorCode.OPERATION_FAIL, "当前订单状态不能确认收货");

        MallOrder order = loadOrder(orderId);
        if (order != null) {
            saveLogistics(order, order.getLogisticsCompany(), order.getLogisticsNo(), "SIGNED", order.getShipTime(), now);
        }
        userNotificationService.notifyUser(user.getId(), null, "ORDER_COMPLETED", "订单已完成",
                "订单《" + (order == null ? orderId : order.getProductTitle()) + "》已完成。", null, orderId);
        return Result.ok(order);
    }

    @Override
    public Result cancelOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = getOrderAndCheckOwner(orderId, user.getId());
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        if (order.getStatus() != STATUS_PENDING_PAY) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "只有待支付订单才能取消");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_CANCELLED)
                .set("cancel_time", now)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("user_id", user.getId())
                .eq("status", STATUS_PENDING_PAY)
                .update();
        if (!updated) throw new BusinessException(ErrorCode.OPERATION_FAIL, "取消订单失败");
        userNotificationService.notifyUser(user.getId(), null, "ORDER_CANCELLED", "订单已取消",
                "你购买的《" + order.getProductTitle() + "》订单已取消。", null, orderId);
        return Result.ok(loadOrder(orderId));
    }

    @Override
    @Transactional
    public Result refundOrder(Long orderId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = getOrderAndCheckOwner(orderId, user.getId());
        if (order == null) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        if (order.getStatus() != STATUS_PAID && order.getStatus() != STATUS_PENDING_SHIP && order.getStatus() != STATUS_SHIPPED) {
            throw new BusinessException(ErrorCode.OPERATION_FAIL, "当前订单状态不能申请退款");
        }
        if (refundMapper.selectCount(new LambdaQueryWrapper<MallRefund>()
                .eq(MallRefund::getOrderId, orderId)
                .in(MallRefund::getStatus, 0, 1)) > 0) {
            throw new BusinessException(ErrorCode.REPEAT_OPERATION, "该订单已有售后处理中");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean updated = update()
                .set("status", STATUS_REFUNDING)
                .set("refund_reason", "用户申请退款")
                .set("update_time", now)
                .eq("id", orderId)
                .eq("user_id", user.getId())
                .in("status", STATUS_PAID, STATUS_PENDING_SHIP, STATUS_SHIPPED)
                .update();
        if (!updated) throw new BusinessException(ErrorCode.OPERATION_FAIL, "申请退款失败");

        MallRefund refund = new MallRefund()
                .setOrderId(orderId)
                .setUserId(user.getId())
                .setMerchantId(order.getMerchantId())
                .setAmount(order.getTotalAmount())
                .setReason("用户申请退款")
                .setStatus(0)
                .setApplyTime(now)
                .setCreateTime(now)
                .setUpdateTime(now);
        refundMapper.insert(refund);
        userNotificationService.notifyUser(user.getId(), null, "ORDER_REFUNDING", "退款申请已提交",
                "你购买的《" + order.getProductTitle() + "》已提交退款申请。", null, orderId);
        notifyMerchant(order.getMerchantId(), "order_refund", order);
        return Result.ok(loadOrder(orderId));
    }

    private boolean decreaseStock(MallOrder order) {
        if (order.getSkuId() != null) {
            return skuMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MallSku>()
                    .setSql("stock = stock - " + order.getQuantity())
                    .setSql("sold = IFNULL(sold, 0) + " + order.getQuantity())
                    .eq(MallSku::getId, order.getSkuId())
                    .ge(MallSku::getStock, order.getQuantity())) > 0;
        }
        return productService.update()
                .setSql("stock = stock - " + order.getQuantity())
                .setSql("sold = IFNULL(sold, 0) + " + order.getQuantity())
                .eq("id", order.getProductId())
                .ge("stock", order.getQuantity())
                .update();
    }

    private void rollbackStock(MallOrder order) {
        if (order.getSkuId() != null) {
            skuMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MallSku>()
                    .setSql("stock = stock + " + order.getQuantity())
                    .setSql("sold = GREATEST(IFNULL(sold, 0) - " + order.getQuantity() + ", 0)")
                    .eq(MallSku::getId, order.getSkuId()));
            return;
        }
        productService.update()
                .setSql("stock = stock + " + order.getQuantity())
                .setSql("sold = GREATEST(IFNULL(sold, 0) - " + order.getQuantity() + ", 0)")
                .eq("id", order.getProductId())
                .update();
    }

    private MallSku loadSku(Long skuId, Long productId) {
        if (skuId == null) {
            return null;
        }
        MallSku sku = skuMapper.selectById(skuId);
        if (sku == null || !productId.equals(sku.getProductId()) || sku.getStatus() == null || sku.getStatus() != 1) {
            throw new IllegalArgumentException("SKU不存在或已下架");
        }
        return sku;
    }

    private UserAddress loadOrderAddress(Long addressId, Long userId) {
        if (addressId != null) {
            UserAddress address = addressMapper.selectById(addressId);
            return address != null && userId.equals(address.getUserId()) ? address : null;
        }
        return addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getDefaultFlag, true)
                .last("limit 1"));
    }

    private MallOrder getOrderAndCheckOwner(Long orderId, Long userId) {
        MallOrder order = getById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            return null;
        }
        return order;
    }

    private MallOrder loadOrder(Long orderId) {
        return getById(orderId);
    }

    private Voucher chooseBestVoucher(MallProduct product, long originalAmount) {
        List<Voucher> vouchers = voucherService.query()
                .eq("status", 1)
                .and(wrapper -> wrapper
                        .eq(product.getMerchantId() != null, "merchant_id", product.getMerchantId())
                        .or()
                        .eq("scope_type", "PLATFORM")
                        .or()
                        .eq(product.getId() != null, "product_id", product.getId())
                        .or()
                        .eq(product.getCategoryId() != null, "category_id", product.getCategoryId())
                        .or()
                        .eq(product.getSubCategoryId() != null, "category_id", product.getSubCategoryId()))
                .list();
        Voucher best = null;
        long bestDiscount = 0;
        for (Voucher voucher : vouchers) {
            if (!isVoucherUsable(voucher, product, originalAmount)) {
                continue;
            }
            long discount = Math.min(safeLong(voucher.getActualValue()), originalAmount);
            if (discount > bestDiscount) {
                best = voucher;
                bestDiscount = discount;
            }
        }
        return best;
    }

    private Voucher resolveVoucher(Long voucherId, MallProduct product, long originalAmount) {
        if (voucherId == null) return null;
        Voucher voucher = voucherService.getById(voucherId);
        if (!isVoucherUsable(voucher, product, originalAmount)) {
            throw new IllegalArgumentException("优惠券不可用");
        }
        return voucher;
    }

    private boolean isVoucherUsable(Voucher voucher, MallProduct product, long originalAmount) {
        if (voucher == null || voucher.getStatus() == null || voucher.getStatus() != 1) {
            return false;
        }
        if (voucher.getPayValue() != null && originalAmount < voucher.getPayValue()) {
            return false;
        }
        if (voucher.getActualValue() == null || voucher.getActualValue() <= 0) {
            return false;
        }
        if ("PLATFORM".equalsIgnoreCase(voucher.getScopeType())) {
            return true;
        }
        if (voucher.getProductId() != null && voucher.getProductId().equals(product.getId())) {
            return true;
        }
        if (voucher.getCategoryId() != null && (voucher.getCategoryId().equals(product.getCategoryId())
                || voucher.getCategoryId().equals(product.getSubCategoryId()))) {
            return true;
        }
        return voucher.getMerchantId() != null && voucher.getMerchantId().equals(product.getMerchantId());
    }

    private void saveLogistics(MallOrder order, String company, String trackingNo, String status,
                               LocalDateTime shipTime, LocalDateTime signedTime) {
        MallLogistics logistics = logisticsMapper.selectOne(new LambdaQueryWrapper<MallLogistics>()
                .eq(MallLogistics::getOrderId, order.getId())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (logistics == null) {
            logistics = new MallLogistics()
                    .setOrderId(order.getId())
                    .setMerchantId(order.getMerchantId())
                    .setUserId(order.getUserId())
                    .setCreateTime(now);
        }
        logistics.setCompany(company == null ? order.getLogisticsCompany() : company);
        logistics.setTrackingNo(trackingNo == null ? order.getLogisticsNo() : trackingNo);
        logistics.setStatus(status);
        logistics.setShipTime(shipTime);
        logistics.setSignedTime(signedTime);
        logistics.setUpdateTime(now);
        if (logistics.getId() == null) {
            logisticsMapper.insert(logistics);
        } else {
            logisticsMapper.updateById(logistics);
        }
    }

    private String firstImage(String images) {
        if (images == null || images.isBlank()) {
            return "";
        }
        return images.split(",")[0].trim();
    }

    private String formatAddress(UserAddress address) {
        return join(address.getProvince(), address.getCity(), address.getDistrict(), address.getDetailAddress());
    }

    private String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private void notifyMerchant(Long merchantId, String type, MallOrder order) {
        if (merchantId == null) return;
        try {
            MerchantNotification notification = new MerchantNotification();
            notification.setMerchantId(merchantId);
            notification.setType(type);
            notification.setOrderId(order.getId());
            notification.setPayload("{\"orderId\":" + order.getId()
                    + ",\"productTitle\":\"" + order.getProductTitle()
                    + "\",\"quantity\":" + order.getQuantity()
                    + ",\"totalAmount\":" + order.getTotalAmount() + "}");
            notification.setReadFlag(false);
            notification.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(notification);
        } catch (Exception e) {
            log.error("通知商家失败, merchantId={}, orderId={}", merchantId, order.getId(), e);
        }
    }
}
