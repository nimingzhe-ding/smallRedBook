package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.ErrorCode;
import com.hmdp.exception.BusinessException;
import com.hmdp.entity.MallCategory;
import com.hmdp.entity.MallFavorite;
import com.hmdp.entity.MallLogistics;
import com.hmdp.entity.MallOrder;
import com.hmdp.entity.MallProduct;
import com.hmdp.entity.MallPromotion;
import com.hmdp.entity.MallRefund;
import com.hmdp.entity.MallReview;
import com.hmdp.entity.MallSku;
import com.hmdp.entity.Merchant;
import com.hmdp.entity.UserAddress;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.MallCategoryMapper;
import com.hmdp.mapper.MallFavoriteMapper;
import com.hmdp.mapper.MallLogisticsMapper;
import com.hmdp.mapper.MallPromotionMapper;
import com.hmdp.mapper.MallRefundMapper;
import com.hmdp.mapper.MallReviewMapper;
import com.hmdp.mapper.MallSkuMapper;
import com.hmdp.service.IMallOrderService;
import com.hmdp.service.IMallProductService;
import com.hmdp.service.IMerchantService;
import com.hmdp.service.IUserNotificationService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.impl.MallOrderServiceImpl;
import com.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城产品化能力接口。
 * 承接类目、SKU、地址、物流、售后、评价、收藏、店铺页和营销活动。
 */
@RestController
@RequestMapping("/mall")
public class MallCommerceController {

    @Resource
    private MallCategoryMapper categoryMapper;
    @Resource
    private MallSkuMapper skuMapper;
    @Resource
    private MallFavoriteMapper favoriteMapper;
    @Resource
    private MallReviewMapper reviewMapper;
    @Resource
    private MallLogisticsMapper logisticsMapper;
    @Resource
    private MallRefundMapper refundMapper;
    @Resource
    private MallPromotionMapper promotionMapper;
    @Resource
    private IMallProductService productService;
    @Resource
    private IMallOrderService orderService;
    @Resource
    private IMerchantService merchantService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IUserNotificationService notificationService;

    @GetMapping("/categories")
    public Result categories() {
        List<MallCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<MallCategory>()
                .eq(MallCategory::getStatus, 1)
                .orderByAsc(MallCategory::getLevel)
                .orderByAsc(MallCategory::getSort)
                .orderByAsc(MallCategory::getId));
        return Result.ok(categories);
    }

    @GetMapping("/products/{id}/skus")
    public Result skus(@PathVariable("id") Long productId) {
        return Result.ok(skuMapper.selectList(new LambdaQueryWrapper<MallSku>()
                .eq(MallSku::getProductId, productId)
                .eq(MallSku::getStatus, 1)
                .orderByAsc(MallSku::getId)));
    }

    @GetMapping("/addresses")
    public Result addresses() {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        return Result.ok(addressList(user.getId()));
    }

    @PostMapping("/addresses")
    public Result saveAddress(@RequestBody UserAddress address) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        checkAddress(address);
        LocalDateTime now = LocalDateTime.now();
        address.setId(null);
        address.setUserId(user.getId());
        address.setDefaultFlag(Boolean.TRUE.equals(address.getDefaultFlag()));
        address.setCreateTime(now);
        address.setUpdateTime(now);
        if (Boolean.TRUE.equals(address.getDefaultFlag())) {
            clearDefaultAddress(user.getId());
        }
        com.baomidou.mybatisplus.core.mapper.BaseMapper<UserAddress> mapper = addressMapper();
        mapper.insert(address);
        return Result.ok(addressList(user.getId()));
    }

    @PutMapping("/addresses/{id}")
    public Result updateAddress(@PathVariable("id") Long id, @RequestBody UserAddress address) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        UserAddress old = addressMapper().selectById(id);
        if (old == null || !user.getId().equals(old.getUserId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "地址不存在");
        if (StrUtil.isNotBlank(address.getReceiverName())) old.setReceiverName(address.getReceiverName());
        if (StrUtil.isNotBlank(address.getPhone())) old.setPhone(address.getPhone());
        if (address.getProvince() != null) old.setProvince(address.getProvince());
        if (address.getCity() != null) old.setCity(address.getCity());
        if (address.getDistrict() != null) old.setDistrict(address.getDistrict());
        if (address.getDetailAddress() != null) old.setDetailAddress(address.getDetailAddress());
        if (address.getDefaultFlag() != null) {
            old.setDefaultFlag(address.getDefaultFlag());
            if (Boolean.TRUE.equals(address.getDefaultFlag())) clearDefaultAddress(user.getId());
        }
        old.setUpdateTime(LocalDateTime.now());
        addressMapper().updateById(old);
        return Result.ok(addressList(user.getId()));
    }

    @DeleteMapping("/addresses/{id}")
    public Result deleteAddress(@PathVariable("id") Long id) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        addressMapper().delete(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, id)
                .eq(UserAddress::getUserId, user.getId()));
        return Result.ok(addressList(user.getId()));
    }

    @PostMapping("/addresses/{id}/default")
    public Result setDefaultAddress(@PathVariable("id") Long id) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        UserAddress address = addressMapper().selectById(id);
        if (address == null || !user.getId().equals(address.getUserId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "地址不存在");
        clearDefaultAddress(user.getId());
        address.setDefaultFlag(true);
        address.setUpdateTime(LocalDateTime.now());
        addressMapper().updateById(address);
        return Result.ok(addressList(user.getId()));
    }

    @PostMapping("/favorites")
    public Result toggleFavorite(@RequestParam("targetType") String targetType, @RequestParam("targetId") Long targetId) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        String type = targetType.trim().toUpperCase();
        LambdaQueryWrapper<MallFavorite> wrapper = new LambdaQueryWrapper<MallFavorite>()
                .eq(MallFavorite::getUserId, user.getId())
                .eq(MallFavorite::getTargetType, type)
                .eq(MallFavorite::getTargetId, targetId);
        MallFavorite existing = favoriteMapper.selectOne(wrapper);
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return Result.ok(false);
        }
        MallFavorite favorite = new MallFavorite()
                .setUserId(user.getId())
                .setTargetType(type)
                .setTargetId(targetId)
                .setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(favorite);
        return Result.ok(true);
    }

    @GetMapping("/favorites")
    public Result favorites(@RequestParam(value = "targetType", required = false) String targetType) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        LambdaQueryWrapper<MallFavorite> wrapper = new LambdaQueryWrapper<MallFavorite>()
                .eq(MallFavorite::getUserId, user.getId())
                .orderByDesc(MallFavorite::getCreateTime);
        if (StrUtil.isNotBlank(targetType)) {
            wrapper.eq(MallFavorite::getTargetType, targetType.trim().toUpperCase());
        }
        return Result.ok(favoriteMapper.selectList(wrapper));
    }

    @PostMapping("/products/{id}/reviews")
    public Result review(@PathVariable("id") Long productId, @RequestBody MallReview review) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = orderService.getById(review.getOrderId());
        if (order == null || !user.getId().equals(order.getUserId()) || !productId.equals(order.getProductId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "只能评价自己的订单商品");
        }
        if (order.getStatus() == null || order.getStatus() != MallOrderServiceImpl.STATUS_COMPLETED) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "确认收货后才能评价");
        }
        if (reviewMapper.selectCount(new LambdaQueryWrapper<MallReview>()
                .eq(MallReview::getOrderId, order.getId())
                .eq(MallReview::getUserId, user.getId())) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该订单已评价");
        }
        LocalDateTime now = LocalDateTime.now();
        review.setId(null);
        review.setProductId(productId);
        review.setSkuId(order.getSkuId());
        review.setUserId(user.getId());
        review.setMerchantId(order.getMerchantId());
        review.setRating(Math.max(1, Math.min(5, review.getRating() == null ? 5 : review.getRating())));
        review.setContent(StrUtil.sub(StrUtil.trimToEmpty(review.getContent()), 0, 500));
        review.setStatus(0);
        review.setCreateTime(now);
        review.setUpdateTime(now);
        reviewMapper.insert(review);
        refreshProductReviewStats(productId);
        return Result.ok(review);
    }

    @GetMapping("/products/{id}/reviews")
    public Result reviews(@PathVariable("id") Long productId) {
        return Result.ok(reviewMapper.selectList(new LambdaQueryWrapper<MallReview>()
                .eq(MallReview::getProductId, productId)
                .eq(MallReview::getStatus, 0)
                .orderByDesc(MallReview::getCreateTime)));
    }

    @GetMapping("/orders/{id}/logistics")
    public Result logistics(@PathVariable("id") Long orderId) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = orderService.getById(orderId);
        if (order == null || !user.getId().equals(order.getUserId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        return Result.ok(logisticsMapper.selectOne(new LambdaQueryWrapper<MallLogistics>()
                .eq(MallLogistics::getOrderId, orderId)
                .last("limit 1")));
    }

    @GetMapping("/orders/{id}")
    public Result orderDetail(@PathVariable("id") Long orderId) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = orderService.getById(orderId);
        if (order == null || !user.getId().equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        detail.put("logistics", logisticsMapper.selectOne(new LambdaQueryWrapper<MallLogistics>()
                .eq(MallLogistics::getOrderId, orderId)
                .last("limit 1")));
        detail.put("refunds", refundMapper.selectList(new LambdaQueryWrapper<MallRefund>()
                .eq(MallRefund::getOrderId, orderId)
                .orderByDesc(MallRefund::getCreateTime)));
        detail.put("product", order.getProductId() == null ? null : productService.getById(order.getProductId()));
        detail.put("voucher", order.getVoucherId() == null ? null : voucherService.getById(order.getVoucherId()));
        detail.put("merchant", order.getMerchantId() == null ? null : merchantService.getById(order.getMerchantId()));
        return Result.ok(detail);
    }

    @PostMapping("/orders/{id}/refunds")
    public Result applyRefund(@PathVariable("id") Long orderId, @RequestBody MallRefund request) {
        Result result = orderService.refundOrder(orderId);
        if (!Boolean.TRUE.equals(result.getSuccess())) return result;
        if (request != null && (StrUtil.isNotBlank(request.getReason()) || StrUtil.isNotBlank(request.getImages()))) {
            MallRefund refund = refundMapper.selectOne(new LambdaQueryWrapper<MallRefund>()
                    .eq(MallRefund::getOrderId, orderId)
                    .orderByDesc(MallRefund::getCreateTime)
                    .last("limit 1"));
            if (refund != null) {
                refund.setReason(StrUtil.blankToDefault(request.getReason(), refund.getReason()));
                refund.setImages(request.getImages());
                refund.setUpdateTime(LocalDateTime.now());
                refundMapper.updateById(refund);
            }
        }
        return result;
    }

    @GetMapping("/orders/{id}/refunds")
    public Result refunds(@PathVariable("id") Long orderId) {
        UserDTO user = currentUser();
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        MallOrder order = orderService.getById(orderId);
        if (order == null || !user.getId().equals(order.getUserId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        return Result.ok(refundMapper.selectList(new LambdaQueryWrapper<MallRefund>()
                .eq(MallRefund::getOrderId, orderId)
                .orderByDesc(MallRefund::getCreateTime)));
    }

    @GetMapping("/merchants/{id}")
    public Result merchantShop(@PathVariable("id") Long merchantId) {
        Merchant merchant = merchantService.getById(merchantId);
        if (merchant == null) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商家不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchant", merchant);
        result.put("products", productService.query()
                .eq("merchant_id", merchantId)
                .eq("status", 1)
                .orderByDesc("sold")
                .orderByDesc("create_time")
                .list());
        result.put("coupons", voucherService.query()
                .eq("merchant_id", merchantId)
                .eq("status", 1)
                .orderByDesc("actual_value")
                .list());
        result.put("promotions", activePromotions(merchantId, null));
        result.put("isFavorite", isFavorite("SHOP", merchantId));
        return Result.ok(result);
    }

    @GetMapping("/promotions")
    public Result promotions(@RequestParam(value = "merchantId", required = false) Long merchantId,
                             @RequestParam(value = "productId", required = false) Long productId) {
        return Result.ok(activePromotions(merchantId, productId));
    }

    @PutMapping("/merchant/products/{id}/skus")
    public Result saveProductSkus(@PathVariable("id") Long productId, @RequestBody List<MallSku> skus) {
        Merchant merchant = currentMerchant();
        if (merchant == null) throw new BusinessException(ErrorCode.NO_PERMISSION, "请先开通商家中心");
        MallProduct product = productService.getById(productId);
        if (product == null || !merchant.getId().equals(product.getMerchantId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商品不存在");
        skuMapper.delete(new LambdaQueryWrapper<MallSku>().eq(MallSku::getProductId, productId));
        LocalDateTime now = LocalDateTime.now();
        if (skus != null) {
            for (MallSku sku : skus) {
                if (sku == null || sku.getPrice() == null || sku.getPrice() <= 0) continue;
                sku.setId(null);
                sku.setProductId(productId);
                sku.setStock(sku.getStock() == null ? 0 : sku.getStock());
                sku.setSold(0);
                sku.setStatus(sku.getStatus() == null ? 1 : sku.getStatus());
                sku.setCreateTime(now);
                sku.setUpdateTime(now);
                skuMapper.insert(sku);
            }
        }
        return skus(productId);
    }

    @PostMapping("/merchant/promotions")
    public Result savePromotion(@RequestBody MallPromotion promotion) {
        Merchant merchant = currentMerchant();
        if (merchant == null) throw new BusinessException(ErrorCode.NO_PERMISSION, "请先开通商家中心");
        if (promotion == null || StrUtil.isBlank(promotion.getTitle()) || StrUtil.isBlank(promotion.getType())) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "活动标题和类型不能为空");
        }
        if (promotion.getProductId() != null) {
            MallProduct product = productService.getById(promotion.getProductId());
            if (product == null || !merchant.getId().equals(product.getMerchantId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商品不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        promotion.setId(null);
        promotion.setMerchantId(merchant.getId());
        promotion.setStatus(promotion.getStatus() == null ? 1 : promotion.getStatus());
        promotion.setBeginTime(promotion.getBeginTime() == null ? now : promotion.getBeginTime());
        promotion.setEndTime(promotion.getEndTime() == null ? now.plusDays(7) : promotion.getEndTime());
        promotion.setCreateTime(now);
        promotion.setUpdateTime(now);
        promotionMapper.insert(promotion);
        return Result.ok(promotion);
    }

    @PostMapping("/merchant/orders/{id}/ship")
    public Result merchantShip(@PathVariable("id") Long orderId,
                               @RequestParam(value = "company", required = false) String company,
                               @RequestParam(value = "trackingNo", required = false) String trackingNo) {
        Merchant merchant = currentMerchant();
        if (merchant == null) throw new BusinessException(ErrorCode.NO_PERMISSION, "请先开通商家中心");
        MallOrder order = orderService.getById(orderId);
        if (order == null || !merchant.getId().equals(order.getMerchantId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "订单不存在");
        LocalDateTime now = LocalDateTime.now();
        boolean updated = orderService.update()
                .set("status", MallOrderServiceImpl.STATUS_SHIPPED)
                .set("ship_time", now)
                .set("logistics_company", company)
                .set("logistics_no", trackingNo)
                .set("update_time", now)
                .eq("id", orderId)
                .eq("merchant_id", merchant.getId())
                .eq("status", MallOrderServiceImpl.STATUS_PENDING_SHIP)
                .update();
        if (!updated) throw new BusinessException(ErrorCode.OPERATION_FAIL, "当前订单状态不能发货");
        saveLogistics(order, company, trackingNo, "SHIPPED", now, null);
        notificationService.notifyUser(order.getUserId(), null, "ORDER_SHIPPED", "商家已发货",
                "你购买的《" + order.getProductTitle() + "》已发货。", null, order.getId());
        return Result.ok(orderService.getById(orderId));
    }

    @PostMapping("/merchant/refunds/{id}/handle")
    public Result handleRefund(@PathVariable("id") Long refundId,
                               @RequestParam("approve") Boolean approve,
                               @RequestParam(value = "remark", required = false) String remark) {
        Merchant merchant = currentMerchant();
        if (merchant == null) throw new BusinessException(ErrorCode.NO_PERMISSION, "请先开通商家中心");
        MallRefund refund = refundMapper.selectById(refundId);
        if (refund == null || !merchant.getId().equals(refund.getMerchantId())) throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "售后单不存在");
        MallOrder order = orderService.getById(refund.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        refund.setStatus(Boolean.TRUE.equals(approve) ? 1 : 2);
        refund.setMerchantRemark(remark);
        refund.setHandleTime(now);
        refund.setRefundTime(Boolean.TRUE.equals(approve) ? now : null);
        refund.setUpdateTime(now);
        refundMapper.updateById(refund);
        if (order != null) {
            orderService.update()
                    .set("status", Boolean.TRUE.equals(approve) ? MallOrderServiceImpl.STATUS_REFUNDED : MallOrderServiceImpl.STATUS_PENDING_SHIP)
                    .set("refund_remark", remark)
                    .set("refund_time", Boolean.TRUE.equals(approve) ? now : null)
                    .set("update_time", now)
                    .eq("id", order.getId())
                    .update();
            notificationService.notifyUser(order.getUserId(), null,
                    Boolean.TRUE.equals(approve) ? "ORDER_REFUND_SUCCESS" : "ORDER_REFUND_REJECTED",
                    Boolean.TRUE.equals(approve) ? "退款成功" : "退款被拒绝",
                    "订单《" + order.getProductTitle() + "》售后已处理。", null, order.getId());
        }
        return Result.ok(refund);
    }

    private Result checkAddress(UserAddress address) {
        if (address == null) throw new BusinessException(ErrorCode.PARAM_EMPTY, "地址不能为空");
        if (StrUtil.isBlank(address.getReceiverName())) throw new BusinessException(ErrorCode.PARAM_EMPTY, "收货人不能为空");
        if (StrUtil.isBlank(address.getPhone())) throw new BusinessException(ErrorCode.PARAM_EMPTY, "手机号不能为空");
        if (StrUtil.isBlank(address.getDetailAddress())) throw new BusinessException(ErrorCode.PARAM_EMPTY, "详细地址不能为空");
        return Result.ok();
    }

    private List<UserAddress> addressList(Long userId) {
        return addressMapper().selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getDefaultFlag)
                .orderByDesc(UserAddress::getUpdateTime));
    }

    private void clearDefaultAddress(Long userId) {
        addressMapper().update(null, new LambdaUpdateWrapper<UserAddress>()
                .set(UserAddress::getDefaultFlag, false)
                .eq(UserAddress::getUserId, userId));
    }

    @Resource
    private com.hmdp.mapper.UserAddressMapper userAddressMapper;

    private com.hmdp.mapper.UserAddressMapper addressMapper() {
        return userAddressMapper;
    }

    private void refreshProductReviewStats(Long productId) {
        List<MallReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<MallReview>()
                .eq(MallReview::getProductId, productId)
                .eq(MallReview::getStatus, 0));
        int count = reviews.size();
        int score = count == 0 ? 0 : (int) Math.round(reviews.stream()
                .mapToInt(review -> review.getRating() == null ? 5 : review.getRating())
                .average()
                .orElse(5) * 10);
        productService.update()
                .set("review_count", count)
                .set("score", score)
                .eq("id", productId)
                .update();
    }

    private boolean isFavorite(String type, Long targetId) {
        UserDTO user = currentUser();
        if (user == null) return false;
        return favoriteMapper.selectCount(new LambdaQueryWrapper<MallFavorite>()
                .eq(MallFavorite::getUserId, user.getId())
                .eq(MallFavorite::getTargetType, type)
                .eq(MallFavorite::getTargetId, targetId)) > 0;
    }

    private List<MallPromotion> activePromotions(Long merchantId, Long productId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MallPromotion> wrapper = new LambdaQueryWrapper<MallPromotion>()
                .eq(MallPromotion::getStatus, 1)
                .le(MallPromotion::getBeginTime, now)
                .ge(MallPromotion::getEndTime, now)
                .orderByDesc(MallPromotion::getDiscountAmount);
        if (merchantId != null) wrapper.eq(MallPromotion::getMerchantId, merchantId);
        if (productId != null) wrapper.and(w -> w.eq(MallPromotion::getProductId, productId).or().isNull(MallPromotion::getProductId));
        return promotionMapper.selectList(wrapper);
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
        logistics.setCompany(company);
        logistics.setTrackingNo(trackingNo);
        logistics.setStatus(status);
        logistics.setShipTime(shipTime);
        logistics.setSignedTime(signedTime);
        logistics.setUpdateTime(now);
        if (logistics.getId() == null) logisticsMapper.insert(logistics);
        else logisticsMapper.updateById(logistics);
    }

    private Merchant currentMerchant() {
        UserDTO user = currentUser();
        if (user == null) return null;
        return merchantService.query().eq("user_id", user.getId()).one();
    }

    private UserDTO currentUser() {
        return UserHolder.getUser();
    }
}
