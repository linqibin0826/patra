package com.patra.starter.test.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/// MyBatis-Plus 测试自动配置。
///
/// 为集成测试提供测试友好的 MyBatis-Plus 配置：
///
/// - **ObjectMapper**：导入 Spring Boot Jackson 自动配置，提供标准配置的 ObjectMapper
/// - **JsonNode TypeHandler**：支持 JSON 字段与 Jackson `JsonNode` 的映射
/// - **测试用拦截器**：包含分页和乐观锁，但**不含** `BlockAttackInnerInterceptor`
/// - **审计字段填充**：提供空的审计上下文处理器
///
/// ### 为什么不包含 BlockAttackInnerInterceptor？
///
/// `BlockAttackInnerInterceptor` 会阻止全表更新/删除操作，
/// 但测试中经常需要执行 `DELETE FROM table` 来清理数据。
///
/// ### 激活条件
///
/// 整个配置类在以下条件下激活：
/// - MyBatis-Plus 核心类（`ConfigurationCustomizer`）
/// - Jackson ObjectMapper
///
/// 各 Bean 有独立的激活条件：
/// - `testJsonTypeHandlerCustomizer`：需要 `JsonToJsonNodeTypeHandler`
/// - `testMetaObjectHandler`：需要 `AuditMetaObjectHandler`
///
/// ### 使用方式
///
/// 在 `@MybatisPlusTest` 切片测试中，通过 `@Import` 导入此配置：
///
/// ```java
/// @MybatisPlusTest
/// @Import(TestMybatisPlusAutoConfiguration.class)
/// class MyRepositoryIT {
///     // ...
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration
@Import(JacksonAutoConfiguration.class)
@ConditionalOnClass(name = {
    "com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer",
    "com.fasterxml.jackson.databind.ObjectMapper"
})
public class TestMybatisPlusAutoConfiguration {

  /// 注册 JsonNode 类型处理器。
  ///
  /// 使测试中可以正确处理 JSON 字段与 `JsonNode` 的映射。
  /// 通过反射创建 `JsonToJsonNodeTypeHandler` 实例以避免编译时依赖。
  /// 仅当 `JsonToJsonNodeTypeHandler` 类存在时才激活。
  ///
  /// @param objectMapper Jackson ObjectMapper
  /// @return 配置定制器
  @Bean
  @ConditionalOnClass(name = "com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler")
  @SuppressWarnings("unchecked")
  ConfigurationCustomizer testJsonTypeHandlerCustomizer(ObjectMapper objectMapper) {
    return configuration -> {
      try {
        Class<?> handlerClass =
            Class.forName("com.patra.starter.mybatis.type.JsonToJsonNodeTypeHandler");
        TypeHandler<JsonNode> handler =
            (TypeHandler<JsonNode>)
                handlerClass.getConstructor(ObjectMapper.class).newInstance(objectMapper);
        configuration.getTypeHandlerRegistry().register(JsonNode.class, handler);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("无法创建 JsonToJsonNodeTypeHandler", e);
      }
    };
  }

  /// 配置测试专用的 MyBatis-Plus 拦截器。
  ///
  /// 包含分页和乐观锁插件，但**不包含** `BlockAttackInnerInterceptor`，
  /// 以允许测试中执行数据清理操作（如 `DELETE FROM table`）。
  ///
  /// 仅当 `PaginationInnerInterceptor` 类存在时才激活（需要 mybatis-plus-jsqlparser 依赖）。
  /// 使用反射创建实例以避免类加载时的 `NoClassDefFoundError`。
  ///
  /// @return 配置了分页和乐观锁的拦截器
  @Bean
  @Primary
  @ConditionalOnClass(name = "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor")
  MybatisPlusInterceptor testMybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    try {
      // 通过反射创建 PaginationInnerInterceptor，避免类加载时的 NoClassDefFoundError
      Class<?> paginationClass =
          Class.forName("com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor");
      Object paginationInterceptor =
          paginationClass.getConstructor(DbType.class).newInstance(DbType.MYSQL);
      interceptor.addInnerInterceptor((InnerInterceptor) paginationInterceptor);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("无法创建 PaginationInnerInterceptor", e);
    }
    interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
    // 注意：不添加 BlockAttackInnerInterceptor，以允许测试清理数据
    return interceptor;
  }

  /// 配置审计字段自动填充处理器。
  ///
  /// 测试环境中使用 null 时钟（使用系统默认时间）。
  /// 通过反射创建 `AuditMetaObjectHandler` 实例以避免编译时依赖。
  /// 仅当 `AuditMetaObjectHandler` 类存在时才激活。
  ///
  /// @return 审计元数据处理器
  @Bean
  @ConditionalOnClass(name = "com.patra.starter.mybatis.handler.AuditMetaObjectHandler")
  @ConditionalOnMissingBean(MetaObjectHandler.class)
  MetaObjectHandler testMetaObjectHandler() {
    try {
      Class<?> handlerClass =
          Class.forName("com.patra.starter.mybatis.handler.AuditMetaObjectHandler");
      // AuditMetaObjectHandler 构造函数接受 @Nullable Clock 参数
      return (MetaObjectHandler)
          handlerClass
              .getConstructor(java.time.Clock.class)
              .newInstance((Object) null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("无法创建 AuditMetaObjectHandler", e);
    }
  }
}
