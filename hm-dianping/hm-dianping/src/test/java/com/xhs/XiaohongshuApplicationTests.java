package com.xhs;

import com.xhs.entity.Shop;
import com.xhs.service.IShopService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xhs.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
@Disabled("Manual Redis GEO data loader. Enable it only when MySQL and Redis are ready.")
public class XiaohongshuApplicationTests {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Test
    public void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();

        // 2.按 typeId 分组
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));

        // 3.写入 Redis GEO
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}
