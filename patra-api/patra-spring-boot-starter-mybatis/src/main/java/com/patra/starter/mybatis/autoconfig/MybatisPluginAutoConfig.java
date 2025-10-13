package com.patra.starter.mybatis.autoconfig;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * Auto-configuration for MyBatis-Plus plugins.
 *
 * <p>This class enables essential plugins for pagination, optimistic locking, and protection
 * against full table operations. It also provides a simple metadata handler for auditing purposes.
 */
@Slf4j
@AutoConfiguration
public class MybatisPluginAutoConfig {

  /**
   * Configures the MyBatis-Plus interceptor chain.
   *
   * @return The configured {@link MybatisPlusInterceptor}.
   */
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    // Add the pagination plugin for MySQL.
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    // Add the optimistic locker plugin to manage versioned data.
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    // Add the block attack plugin to prevent full table updates and deletes.
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    return interceptor;
  }

  /**
   * Provides a {@link MetaObjectHandler} to automatically populate audit fields.
   *
   * @param clock An optional {@link Clock} for time-sensitive testing.
   * @return The configured {@link MetaObjectHandler}.
   */
  @Bean
  public MetaObjectHandler metaObjectHandler(@Nullable Clock clock) {
    log.info("Initializing MyBatis-Plus MetaObjectHandler.");
    return new MetaObjectHandler() {
      @Override
      public void insertFill(MetaObject metaObject) {
        // Populate createdAt and updatedAt fields on insertion.
        Instant now = Objects.isNull(clock) ? Instant.now() : Instant.now(clock);
        this.strictInsertFill(metaObject, "createdAt", () -> now, Instant.class);
        this.strictInsertFill(metaObject, "updatedAt", () -> now, Instant.class);

        // TODO: Populate createdBy and updatedBy fields with user information from the current
        // security context.
      }

      @Override
      public void updateFill(MetaObject metaObject) {
        // Populate the updatedAt field on update.
        Instant now = Objects.isNull(clock) ? Instant.now() : Instant.now(clock);
        this.strictUpdateFill(metaObject, "updatedAt", () -> now, Instant.class);

        // TODO: Populate the updatedBy field with user information from the current security
        // context.
      }
    };
  }
}
