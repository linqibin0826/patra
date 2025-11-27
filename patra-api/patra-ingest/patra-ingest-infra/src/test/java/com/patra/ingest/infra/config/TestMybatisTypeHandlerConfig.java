package com.patra.ingest.infra.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.mybatis.handler.AuditMetaObjectHandler;
import com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// 测试专用 MyBatis-Plus 配置。
///
/// 为集成测试提供：
/// - JSON 字段映射所需的 TypeHandler
/// - 测试友好的拦截器（不含 BlockAttackInnerInterceptor）
/// - 审计字段自动填充处理器
///
/// 简化测试配置，避免依赖完整的 `PatraMybatisAutoConfiguration`。
///
/// @author linqibin
/// @since 0.1.0
@Configuration
@ConditionalOnClass(ConfigurationCustomizer.class)
public class TestMybatisTypeHandlerConfig {

  /// 注册 JsonNode 类型处理器。
  ///
  /// @param objectMapper Jackson ObjectMapper
  /// @return ConfigurationCustomizer
  @Bean
  ConfigurationCustomizer testJsonTypeHandlerCustomizer(ObjectMapper objectMapper) {
    return configuration ->
        configuration
            .getTypeHandlerRegistry()
            .register(JsonNode.class, new JsonToJsonNodeTypeHandler(objectMapper));
  }

  /// 配置测试专用的 MyBatis-Plus 拦截器。
  ///
  /// 包含分页和乐观锁插件，但**不包含** BlockAttackInnerInterceptor，
  /// 以允许测试中执行清理操作（如 DELETE 全表）。
  ///
  /// @return 配置了分页和乐观锁的拦截器
  @Bean
  @Primary
  MybatisPlusInterceptor testMybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    // 注意：不添加 BlockAttackInnerInterceptor，以允许测试清理数据
    return interceptor;
  }

  /// 配置审计字段自动填充处理器。
  ///
  /// @return 审计元数据处理器
  @Bean
  MetaObjectHandler testMetaObjectHandler() {
    return new AuditMetaObjectHandler(null);
  }
}
