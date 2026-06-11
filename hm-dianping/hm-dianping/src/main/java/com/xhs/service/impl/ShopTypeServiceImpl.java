package com.xhs.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xhs.dto.Result;
import com.xhs.entity.ShopType;
import com.xhs.mapper.ShopTypeMapper;
import com.xhs.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
//1.从redis缓存中查询店铺类型缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        //2.如果存在，直接返回
        if(StrUtil.isNotBlank(shopTypeJson)){
            //存在，则直接返回
            List<ShopType> typeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(typeList);
        }
        //3.不存在，查询数据库，并将数据存入redis缓存
        List<ShopType> typeList = query().orderByAsc("sort").list();
        //判断数据库是否存在
        if(typeList == null || typeList.size() == 0){
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "店铺类型不存在！");
        }
        //存在，存入redis缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        //返回数据
        return Result.ok(typeList);
    }
}
