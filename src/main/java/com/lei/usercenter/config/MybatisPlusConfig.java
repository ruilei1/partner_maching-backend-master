package com.lei.usercenter.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.lei.usercenter.mapper")
public class MybatisPlusConfig {

    /**
     * 新的分页插件，一级缓存和二级缓存遵循mybatis的缓存逻辑，
     * 需要设置MybatisConfiguration#useDeprecatedExecutor = false
     * 避免缓存出现问题（该属性会在旧插件移除后一同移除）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
