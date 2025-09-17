package com.patra.starter.mybatis.autoconfig;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Mybatis-Plus 插件自动配置
 */
@Slf4j
@AutoConfiguration
public class MybatisPluginAutoConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 防止全表更新与删除插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler(@Nullable Clock clock) {
        log.info("初始化 Mybatis-Plus MetaObjectHandler");
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                // 填充创建和更新时间
                this.strictInsertFill(
                        metaObject,
                        "createdAt",
                        () -> Objects.isNull(clock) ? Instant.now() : Instant.now(clock),
                        Instant.class);
                this.strictInsertFill(
                        metaObject,
                        "updatedAt",
                        () -> Objects.isNull(clock) ? Instant.now() : Instant.now(clock),
                        Instant.class);

                // 填充创建人和更新人信息
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 填充更新时间
                this.strictUpdateFill(metaObject, "updatedAt", () -> Objects.isNull(clock) ? Instant.now() : Instant.now(clock), Instant.class);

            }
        };
    }
}
