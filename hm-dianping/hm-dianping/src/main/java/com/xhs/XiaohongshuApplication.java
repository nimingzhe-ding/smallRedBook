package com.xhs;

import com.xhs.entity.Shop;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.xhs.mapper")
@SpringBootApplication
public class XiaohongshuApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaohongshuApplication.class, args);

    }

}
