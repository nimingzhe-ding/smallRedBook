package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.Result;
import com.xhs.entity.MallProduct;

/**
 * 商城商品服务。
 */
public interface IMallProductService extends IService<MallProduct> {
    Result pageProducts(String category, String query, Integer current);

    Result detail(Long id);
}
