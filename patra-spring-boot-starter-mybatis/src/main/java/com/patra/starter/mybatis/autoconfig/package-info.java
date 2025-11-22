/// MyBatis 框架自动配置包。
///
/// 本包负责 Patra 项目中 MyBatis-Plus 集成的自动配置和约定设置。它提供开箱即用的功能,包括 Mapper 扫描、类型处理器注册和数据层异常映射。
///
/// ## 职责
///
/// - 自动扫描基础设施层的 Mapper 接口,遵循约定路径 `com.patra.**.infra.persistence.mapper`
///   - 注册自定义 TypeHandler,支持 JSON 字段与 Java 对象的自动映射
///   - 配置数据层异常到标准 HTTP 错误码的转换策略
///   - 提供可扩展的配置点,业务模块可覆盖默认行为
///
/// ## 核心组件
///
/// - {@link com.patra.starter.mybatis.autoconfig.PatraMybatisAutoConfiguration} - 主配置类,定义 Mapper
///       扫描和类型处理器注册
///   - {@link com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig} - 插件配置类,注册元数据处理器和分页拦截器
///
/// ## 设计决策
///
/// - **约定优于配置:** 默认扫描 `com.patra.**.infra.persistence.mapper` 包,符合项目六边形架构规范
///   - **依赖注入:** 使用 Spring 管理的 {@link com.fasterxml.jackson.databind.ObjectMapper} 确保 JSON
///       处理的一致性
///   - **可扩展性:** 业务模块可通过 MyBatis-Plus 标准配置属性扩展 Mapper 路径和类型处理器
///   - **条件装配:** 使用 `@ConditionalOnClass` 和 `@ConditionalOnMissingBean` 避免冲突
///
/// ## 配置示例
///
/// **默认行为(零配置):**
///
/// ```
///
/// // 自动扫描所有模块的 Mapper 接口
/// // com.patra.registry.infra.persistence.mapper.ProvenanceMapper ✓
/// // com.patra.ingest.infra.persistence.mapper.PublicationMapper ✓
///
/// ```
///
/// **扩展 Mapper 扫描路径:**
///
/// ```
///
/// mybatis-plus:
///   mapper-locations: classpath*:/mapper/&#42;&#42;/&#42;.xml
///   type-aliases-package: com.example.custom.entity
///
/// ```
///
/// **注册自定义 TypeHandler:**
///
/// ```
///
/// &#64;Bean
/// public ConfigurationCustomizer customTypeHandlerCustomizer() {
///     return configuration -&gt; {
///         configuration.getTypeHandlerRegistry()
///             .register(LocalDate.class, new LocalDateTypeHandler());
///     };
/// }
///
/// ```
///
/// ## 相关模块
///
/// - {@link com.patra.starter.mybatis.handler} - 元数据自动填充处理器
///   - {@link com.patra.starter.mybatis.type} - 自定义类型转换器
///   - {@link com.patra.starter.mybatis.error.contributor} - 数据层异常映射
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.mybatis.autoconfig;
