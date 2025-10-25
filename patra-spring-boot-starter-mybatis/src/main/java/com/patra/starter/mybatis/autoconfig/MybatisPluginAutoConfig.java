package com.patra.starter.mybatis.autoconfig;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.patra.starter.mybatis.handler.AuditMetaObjectHandler;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.lang.Nullable;

/**
 * Auto-configuration for MyBatis-Plus plugins.
 *
 * <p>This class enables essential plugins for pagination, optimistic locking, and protection
 * against full table operations. It also provides a metadata handler for automatic audit field
 * population.
 */
@Slf4j
@AutoConfiguration
public class MybatisPluginAutoConfig {

  /**
   * Configures the MyBatis-Plus interceptor chain with essential plugins.
   *
   * @param additionalInterceptorsProvider provider for custom interceptors from application context
   * @return configured interceptor with pagination, optimistic locking, and attack protection
   */
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor(
      ObjectProvider<InnerInterceptor> additionalInterceptorsProvider) {
    log.info("Initializing MyBatis-Plus interceptor with standard plugins");
    MybatisPlusInterceptor interceptor = createStandardInterceptor();
    registerAdditionalInterceptors(interceptor, additionalInterceptorsProvider);
    return interceptor;
  }

  /**
   * Provides a metadata handler to automatically populate audit fields during insert and update.
   *
   * @param clock optional clock for time-sensitive testing, null uses system default
   * @return configured metadata handler
   */
  @Bean
  public MetaObjectHandler metaObjectHandler(@Nullable Clock clock) {
    log.info("Initializing MyBatis-Plus audit metadata handler");
    return new AuditMetaObjectHandler(clock);
  }

  /**
   * Creates a standard interceptor with pagination, optimistic locking, and attack protection.
   *
   * @return interceptor with standard plugins configured
   */
  private MybatisPlusInterceptor createStandardInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    log.debug(
        "Registered standard plugins: PaginationInterceptor, OptimisticLockerInterceptor, "
            + "BlockAttackInterceptor");
    return interceptor;
  }

  /**
   * Registers additional custom interceptors provided by the application.
   *
   * @param interceptor the interceptor to register into
   * @param provider provider for additional interceptors
   */
  private void registerAdditionalInterceptors(
      MybatisPlusInterceptor interceptor, ObjectProvider<InnerInterceptor> provider) {
    provider
        .orderedStream()
        .forEach(
            inner -> {
              try {
                interceptor.addInnerInterceptor(inner);
                log.info("Registered additional InnerInterceptor: {}", inner.getClass().getName());
              } catch (Exception e) {
                log.warn(
                    "Failed to register InnerInterceptor {}: {}",
                    inner.getClass().getName(),
                    e.getMessage());
              }
            });
  }
}
