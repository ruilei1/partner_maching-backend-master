package com.lei.usercenter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 *
 * @author lei
 */
@EnableFileStorage
@SpringBootApplication
@EnableScheduling // 开启spring定时任务功能
public class partner_Application {
    public static void main(String[] args) {
        SpringApplication.run(partner_Application.class, args);
    }

}
