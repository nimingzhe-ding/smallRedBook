package com.xhs.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xhs.dto.MallCartRequest;
import com.xhs.dto.Result;
import com.xhs.entity.MallCartItem;

/**
 * 商城购物车服务。
 */
public interface IMallCartService extends IService<MallCartItem> {
    Result add(MallCartRequest request);

    Result listMine();

    Result removeItem(Long id);
}
