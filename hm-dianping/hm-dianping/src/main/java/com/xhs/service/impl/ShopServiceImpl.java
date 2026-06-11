package com.xhs.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.json.JSONUtil;
import com.xhs.dto.Result;
import com.xhs.entity.Shop;
import com.xhs.enums.ErrorCode;
import com.xhs.exception.BusinessException;
import com.xhs.mapper.ShopMapper;
import com.xhs.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhs.utils.CacheClient;
import com.xhs.utils.RedisConstants;
import com.xhs.utils.RedisData;
import com.xhs.utils.SystemConstants;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedissonClient redissonClient;
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透解决方案
        //Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY,id, Shop.class,this::getById,30L, TimeUnit.MINUTES);
        //缓存击穿（互斥锁）解决方案
//
   Shop shop = queryWithMutex(id);
        //（逻辑过期）解决方案
        //Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,id, Shop.class,30L, TimeUnit.SECONDS,this::getById);
        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_EXIST);
        }
        //6.返回
        return Result.ok(shop);
    }

    /**
     * 将店铺信息保存到redis
     * @param id
     * @param expireSeconds
     */
    private  void saveShopToRedis(Long id,Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
    /**
     * 更新商铺信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_EMPTY, "店铺id不能为空！");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY +id);
        return Result.ok(shop);
    }
    /**
     * 根据id查询商铺信息-缓存击穿解决方案
     * @param id
     * @return
     */
    public Shop  queryWithMutex(Long id){
        //1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //2.如果存在，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //2.1.命中的是空值，返回错误
        if (shopJson != null) {
            return null;
        }
        //实现缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        RLock lock = redissonClient.getLock(lockKey);
        Shop shop = null;
        try {
            boolean isLock = lock.tryLock();
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",2L, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return shop;
    }

    /**
     * 根据id查询商铺信息-逻辑过期解决方案
     * @param id
     * @return
     */
//    public Shop  queryWithLogicalExpire(Long id){
//        //1.从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        //2.如果存在，直接返回
//        if (StrUtil.isBlank(shopJson)) {
//
//            return  null;
//        }
//        //命中，需要将json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONUtil.toJsonStr(redisData.getData())), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //3.判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //3.1.未过期，直接返回店铺信息
//            return shop;
//        }
//        //3.2.已过期，需要缓存重建
//        //4.获取互斥锁
//        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
//        boolean isLock = tryLock(lockKey);
//        //5.判断是否获取锁成功
//
//        if(!isLock){
//            //5.1.失败，直接返回过期的店铺信息
//            return shop;
//        }
//        //5.2.成功，开启独立线程，实现缓存重建
//        //注意：这里使用线程池会更好一些
//        new Thread(()->{
//            try {
//                //重建缓存
//                saveShopToRedis(id,30L);
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }finally {
//                //释放锁
//                unlock(lockKey);
//            }
//        }).start();
//        //6.返回过期的店铺信息
//        return shop;
//    }
    public Shop queryWithLogicalExpire(Long id) {
        // 1. 从 redis 中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);

        // 2. 判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            // 逻辑过期方案假定热点 Key 已预热，如果不存在直接返回 null
            return null;
        }

        // 3. 命中，反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 这里是关键！从 RedisData 的 data 字段（JSONObject）转为 Shop
        Object data = redisData.getData();
        Shop shop = JSONUtil.toBean(JSONUtil.parseObj(data), Shop.class);

        LocalDateTime expireTime = redisData.getExpireTime();

        // 4. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回
            return shop;
        }

        // 5. 已过期，缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock();
        if (isLock) {
            new Thread(() -> {
                try {
                    this.saveShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }).start();
        }
        // 获取锁失败或正在重建，都先返回旧数据
        return shop;
    }

    /**
     * 根据id查询商铺信息-缓存穿透解决方案
     * @param id
     * @return
     */
//    public Shop  queryWithPassThrough(Long id){
//        //1.从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
//        //2.如果存在，直接返回
//        if (StrUtil.isNotBlank(shopJson)) {
//            //存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return  shop;
//        }
//        //2.1.命中的是空值，返回错误
//        if (shopJson != null) {
//            return null;
//        }
//        //3.不存在，根据id查询数据库
//        Shop shop = getById(id);
//
//        //4.不存在，返回错误
//        if (shop == null) {
//            //将空值写入到redis缓存，防止缓存穿透
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "",2L, TimeUnit.MINUTES);
//            return null;
//        }
//        //5.存在，写入redis缓存
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
//        //6.返回
//        return shop;
//    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标来进行查询
        if(x == null || y == null){
            //不需要坐标查询，直接根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                        .page(new Page<>(current,SystemConstants.DEFAULT_PAGE_SIZE));
            //返回数据
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis，按照距离排序分页。shopId，distance
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        //解析出来Id
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size()<=from){
            return Result.ok(Collections.emptyList());
        }
        //根据id查询shop
        List<Long> ids = new ArrayList<>(list.size());
        Map<String,Distance> map = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result-> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            map.put(shopIdStr, distance);
        });
        List<Shop> shops = query().in("id", ids).last("order by field(id," + StrUtil.join(",", ids) + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(map.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }

    /**
     * 启动时加载所有店铺坐标到全局 GEO 集合，供附近流使用。
     */
    @PostConstruct
    public void loadAllShopGeo() {
        try {
            List<Shop> shops = list();
            if (shops == null || shops.isEmpty()) return;
            String key = RedisConstants.SHOP_GEO_ALL_KEY;
            for (Shop shop : shops) {
                if (shop.getX() != null && shop.getY() != null) {
                    stringRedisTemplate.opsForGeo().add(key,
                            new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                                    new org.springframework.data.geo.Point(shop.getX(), shop.getY())));
                }
            }
        } catch (Exception e) {
            // GEO 加载失败不影响启动
        }
    }
}
