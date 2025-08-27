package com.lei.usercenter.service;

import com.lei.usercenter.model.domain.User;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.annotation.Resource;

@SpringBootTest
public class RedisTest {

    @Resource
    private RedisTemplate redisTemplate;


    @Test
    void testRedis() {
        ValueOperations valueOperations = redisTemplate.opsForValue();
        // 增
        valueOperations.set("name", "leirui");
        valueOperations.set("age", 16);
        valueOperations.set("hight", 1.75);
        User user = new User();
        user.setId(2L);
        user.setUsername("lei");
        valueOperations.set("lei-user", user);

        // 查
        Object user1 = valueOperations.get("name");
        Assertions.assertTrue("leirui".equals((String) user1));
        user1 = valueOperations.get("age");
        Assertions.assertTrue(16 == (Integer) user1);
        user1 = valueOperations.get("hight");
        Assertions.assertTrue(1.75 == (Double) user1);
        System.out.println(valueOperations.get("lei-user"));

        //删
        redisTemplate.delete("name");
    }


}
