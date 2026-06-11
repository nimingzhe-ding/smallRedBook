package com.xhs.ai.tool;

import cn.hutool.core.util.StrUtil;
import com.xhs.dto.UserDTO;
import com.xhs.entity.Shop;
import com.xhs.entity.ShopType;
import com.xhs.entity.Voucher;
import com.xhs.entity.VoucherOrder;
import com.xhs.mapper.VoucherMapper;
import com.xhs.service.IShopService;
import com.xhs.service.IShopTypeService;
import com.xhs.service.IVoucherOrderService;
import com.xhs.service.IVoucherService;
import com.xhs.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class XiaohongshuAiTools {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IShopService shopService;
    private final IShopTypeService shopTypeService;
    private final IVoucherService voucherService;
    private final IVoucherOrderService voucherOrderService;
    private final VoucherMapper voucherMapper;

    @Tool(description = "根据店铺关键字查询店铺列表，可用于名称、商圈或地址的模糊匹配")
    public List<Map<String, Object>> searchShopsByKeyword(
            @ToolParam(description = "店铺关键字，比如 火锅、KTV、Mamala、运河上街") String keyword,
            @ToolParam(description = "返回条数，建议 1 到 10", required = false) Integer limit) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        int finalLimit = normalizeLimit(limit);
        List<Shop> shops = shopService.lambdaQuery()
                .and(wrapper -> wrapper.like(Shop::getName, keyword)
                        .or()
                        .like(Shop::getArea, keyword)
                        .or()
                        .like(Shop::getAddress, keyword))
                .orderByDesc(Shop::getScore)
                .last("limit " + finalLimit)
                .list();
        return shops.stream().map(this::toShopSummary).toList();
    }

    @Tool(description = "根据店铺分类名称查询店铺列表，可用于推荐同类高评分商户")
    public List<Map<String, Object>> searchShopsByTypeName(
            @ToolParam(description = "店铺分类名称，比如 美食、KTV、购物") String typeName,
            @ToolParam(description = "返回条数，建议 1 到 10", required = false) Integer limit) {
        if (StrUtil.isBlank(typeName)) {
            return Collections.emptyList();
        }
        int finalLimit = normalizeLimit(limit);
        List<Long> typeIds = shopTypeService.lambdaQuery()
                .like(ShopType::getName, typeName)
                .list()
                .stream()
                .map(ShopType::getId)
                .toList();
        if (typeIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Shop> shops = shopService.lambdaQuery()
                .in(Shop::getTypeId, typeIds)
                .orderByDesc(Shop::getScore)
                .orderByDesc(Shop::getSold)
                .last("limit " + finalLimit)
                .list();
        return shops.stream().map(this::toShopSummary).toList();
    }

    @Tool(description = "根据店铺ID查询店铺详情")
    public Map<String, Object> getShopDetail(
            @ToolParam(description = "店铺ID") Long shopId) {
        if (shopId == null) {
            return Collections.emptyMap();
        }
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> detail = new LinkedHashMap<>(toShopSummary(shop));
        detail.put("images", shop.getImages());
        detail.put("longitude", shop.getX());
        detail.put("latitude", shop.getY());
        return detail;
    }

    @Tool(description = "根据店铺ID查询该店铺可用的优惠券信息")
    public List<Map<String, Object>> getShopVouchers(
            @ToolParam(description = "店铺ID") Long shopId) {
        if (shopId == null) {
            return Collections.emptyList();
        }
        List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);
        return vouchers.stream().map(this::toVoucherInfo).toList();
    }

    @Tool(description = "查询当前系统支持的店铺分类列表")
    public List<Map<String, Object>> listShopTypes() {
        return shopTypeService.lambdaQuery()
                .orderByAsc(ShopType::getSort)
                .list()
                .stream()
                .map(type -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("typeId", type.getId());
                    item.put("typeName", type.getName());
                    item.put("sort", type.getSort());
                    return item;
                })
                .toList();
    }

    @Tool(description = "根据用户ID查询最近的团购券订单，适用于“我的订单”或“订单状态”场景")
    public List<Map<String, Object>> getRecentVoucherOrdersByUserId(
            @ToolParam(description = "登录用户ID；只有已登录用户才能查询自己的订单") Long userId,
            @ToolParam(description = "返回条数，建议 1 到 10", required = false) Integer limit) {
        UserDTO currentUser = UserHolder.getUser();
        if (currentUser == null || currentUser.getId() == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("needLogin", true);
            result.put("message", "请先登录后再查询自己的订单");
            return List.of(result);
        }
        Long currentUserId = currentUser.getId();
        int finalLimit = normalizeLimit(limit);
        List<VoucherOrder> orders = voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getUserId, currentUserId)
                .orderByDesc(VoucherOrder::getCreateTime)
                .last("limit " + finalLimit)
                .list();
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> voucherIds = orders.stream().map(VoucherOrder::getVoucherId).distinct().toList();
        Map<Long, Voucher> voucherMap = voucherService.listByIds(voucherIds).stream()
                .collect(Collectors.toMap(Voucher::getId, Function.identity(), (left, right) -> left));
        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (VoucherOrder order : orders) {
            Voucher voucher = voucherMap.get(order.getVoucherId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getId());
            item.put("voucherId", order.getVoucherId());
            item.put("voucherTitle", voucher == null ? null : voucher.getTitle());
            item.put("status", resolveOrderStatus(order.getStatus()));
            item.put("payType", resolvePayType(order.getPayType()));
            item.put("createTime", order.getCreateTime() == null ? null : DATE_TIME_FORMATTER.format(order.getCreateTime()));
            item.put("payTime", order.getPayTime() == null ? null : DATE_TIME_FORMATTER.format(order.getPayTime()));
            item.put("useTime", order.getUseTime() == null ? null : DATE_TIME_FORMATTER.format(order.getUseTime()));
            item.put("refundTime", order.getRefundTime() == null ? null : DATE_TIME_FORMATTER.format(order.getRefundTime()));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> toShopSummary(Shop shop) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("shopId", shop.getId());
        item.put("shopName", shop.getName());
        item.put("typeId", shop.getTypeId());
        item.put("area", shop.getArea());
        item.put("address", shop.getAddress());
        item.put("avgPriceYuan", yuan(shop.getAvgPrice()));
        item.put("score", score(shop.getScore()));
        item.put("sold", shop.getSold());
        item.put("comments", shop.getComments());
        item.put("openHours", shop.getOpenHours());
        return item;
    }

    private Map<String, Object> toVoucherInfo(Voucher voucher) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("voucherId", voucher.getId());
        item.put("title", voucher.getTitle());
        item.put("subTitle", voucher.getSubTitle());
        item.put("rules", voucher.getRules());
        item.put("type", voucher.getType() != null && voucher.getType() == 1 ? "秒杀券" : "普通券");
        item.put("payValueYuan", yuan(voucher.getPayValue()));
        item.put("actualValueYuan", yuan(voucher.getActualValue()));
        item.put("stock", voucher.getStock());
        item.put("beginTime", voucher.getBeginTime() == null ? null : DATE_TIME_FORMATTER.format(voucher.getBeginTime()));
        item.put("endTime", voucher.getEndTime() == null ? null : DATE_TIME_FORMATTER.format(voucher.getEndTime()));
        return item;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 5;
        }
        return Math.min(limit, 10);
    }

    private Double yuan(Long cents) {
        if (cents == null) {
            return null;
        }
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Double score(Integer score) {
        if (score == null) {
            return null;
        }
        return BigDecimal.valueOf(score)
                .divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String resolveOrderStatus(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case 1 -> "未支付";
            case 2 -> "已支付";
            case 3 -> "已核销";
            case 4 -> "已取消";
            case 5 -> "退款中";
            case 6 -> "已退款";
            default -> "未知状态";
        };
    }

    private String resolvePayType(Integer payType) {
        if (payType == null) {
            return "未知支付方式";
        }
        return switch (payType) {
            case 1 -> "余额支付";
            case 2 -> "支付宝";
            case 3 -> "微信";
            default -> "未知支付方式";
        };
    }
}
