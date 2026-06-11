package com.xhs.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.dto.Result;
import com.xhs.dto.UserDTO;
import com.xhs.entity.MallCategory;
import com.xhs.entity.MallFavorite;
import com.xhs.entity.MallProduct;
import com.xhs.entity.MallReview;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.entity.MallSku;
import com.xhs.entity.Voucher;
import com.xhs.mapper.MallCategoryMapper;
import com.xhs.mapper.MallFavoriteMapper;
import com.xhs.mapper.MallProductMapper;
import com.xhs.mapper.MallReviewMapper;
import com.xhs.mapper.MallSkuMapper;
import com.xhs.service.IMallProductService;
import com.xhs.service.IMerchantService;
import com.xhs.service.IVoucherService;
import com.xhs.utils.SystemConstants;
import com.xhs.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城商品服务实现。
 * 详情页一次返回轮播图、SKU、评价、优惠券、商家和类目信息，前端不用再拼多组接口。
 */
@Service
public class MallProductServiceImpl extends ServiceImpl<MallProductMapper, MallProduct> implements IMallProductService {

    @Resource
    private MallSkuMapper skuMapper;

    @Resource
    private MallReviewMapper reviewMapper;

    @Resource
    private MallCategoryMapper categoryMapper;

    @Resource
    private MallFavoriteMapper favoriteMapper;

    @Resource
    private IMerchantService merchantService;

    @Resource
    private IVoucherService voucherService;

    @Override
    public Result pageProducts(String category, String query, Integer current) {
        int pageNo = current == null || current < 1 ? 1 : current;
        Long categoryId = parseLong(category);
        Page<MallProduct> page = query()
                .eq("status", 1)
                .and(StrUtil.isNotBlank(category) && !"all".equals(category), wrapper -> {
                    if (categoryId == null) {
                        wrapper.eq("category", category);
                    } else {
                        wrapper.eq("category_id", categoryId).or().eq("sub_category_id", categoryId);
                    }
                })
                .and(StrUtil.isNotBlank(query), wrapper -> wrapper.like("title", query).or().like("sub_title", query))
                .orderByDesc("sold")
                .orderByDesc("create_time")
                .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result detail(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "商品ID不能为空");
        }
        MallProduct product = getById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "商品不存在");
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("product", product);
        detail.put("skus", skuMapper.selectList(new LambdaQueryWrapper<MallSku>()
                .eq(MallSku::getProductId, id)
                .eq(MallSku::getStatus, 1)
                .orderByAsc(MallSku::getId)));
        detail.put("reviews", reviewMapper.selectList(new LambdaQueryWrapper<MallReview>()
                .eq(MallReview::getProductId, id)
                .eq(MallReview::getStatus, 0)
                .orderByDesc(MallReview::getCreateTime)
                .last("limit 20")));
        detail.put("coupons", listUsableCoupons(product));
        detail.put("merchant", product.getMerchantId() == null ? null : merchantService.getById(product.getMerchantId()));
        detail.put("category", loadCategory(product.getCategoryId()));
        detail.put("subCategory", loadCategory(product.getSubCategoryId()));
        detail.put("isFavorite", isFavorite(id));
        return Result.ok(detail);
    }

    private MallCategory loadCategory(Long categoryId) {
        return categoryId == null ? null : categoryMapper.selectById(categoryId);
    }

    private List<Voucher> listUsableCoupons(MallProduct product) {
        return voucherService.query()
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
                .orderByDesc("actual_value")
                .list();
    }

    private boolean isFavorite(Long productId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return false;
        }
        return favoriteMapper.selectCount(new LambdaQueryWrapper<MallFavorite>()
                .eq(MallFavorite::getUserId, user.getId())
                .eq(MallFavorite::getTargetType, "PRODUCT")
                .eq(MallFavorite::getTargetId, productId)) > 0;
    }

    private Long parseLong(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
