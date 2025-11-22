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

/// MyBatis-Plus 插件的自动配置类。
///
/// 该配置类启用分页、乐观锁和全表操作防护等核心插件,同时提供审计字段自动填充的元数据处理器。
@Slf4j
@AutoConfiguration
public class MybatisPluginAutoConfig {

  /// 配置 MyBatis-Plus 拦截器链,包含核心插件。
  ///
  /// @param additionalInterceptorsProvider 来自应用上下文的自定义拦截器提供者
  /// @return 配置了分页、乐观锁和攻击防护的拦截器
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor(
      ObjectProvider<InnerInterceptor> additionalInterceptorsProvider) {
    log.info("初始化 MyBatis-Plus 拦截器及标准插件");
    MybatisPlusInterceptor interceptor = createStandardInterceptor();
    registerAdditionalInterceptors(interceptor, additionalInterceptorsProvider);
    return interceptor;
  }

  /// 提供元数据处理器,在插入和更新时自动填充审计字段。
  ///
  /// @param clock 可选的时钟,用于时间敏感型测试,null 则使用系统默认值
  /// @return 配置好的元数据处理器
  @Bean
  public MetaObjectHandler metaObjectHandler(@Nullable Clock clock) {
    log.info("初始化 MyBatis-Plus 审计元数据处理器");
    return new AuditMetaObjectHandler(clock);
  }

  /// 创建包含分页、乐观锁和攻击防护的标准拦截器。
  ///
  /// @return 配置好标准插件的拦截器
  private MybatisPlusInterceptor createStandardInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    log.debug(
        "已注册标准插件: PaginationInterceptor, OptimisticLockerInterceptor, " + "BlockAttackInterceptor");
    return interceptor;
  }

  /// 注册应用程序提供的额外自定义拦截器。
  ///
  /// @param interceptor 要注册到的拦截器
  /// @param provider 额外拦截器的提供者
  private void registerAdditionalInterceptors(
      MybatisPlusInterceptor interceptor, ObjectProvider<InnerInterceptor> provider) {
    provider
        .orderedStream()
        .forEach(
            inner -> {
              try {
                interceptor.addInnerInterceptor(inner);
                log.info("已注册额外的 InnerInterceptor: {}", inner.getClass().getName());
              } catch (Exception e) {
                log.warn(
                    "注册 InnerInterceptor {} 失败: {}", inner.getClass().getName(), e.getMessage());
              }
            });
  }
}
