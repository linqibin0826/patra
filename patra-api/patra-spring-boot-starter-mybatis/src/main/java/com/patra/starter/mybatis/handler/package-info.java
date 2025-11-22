/// MyBatis 元数据自动填充处理器包。
///
/// 本包提供 MyBatis-Plus 的 {@link com.baomidou.mybatisplus.core.handlers.MetaObjectHandler}
/// 实现,用于在插入和更新操作时自动填充审计字段(如创建时间、更新时间、操作人等)。
///
/// ## 职责
///
/// - 在插入操作时自动填充创建时间和更新时间
///   - 在更新操作时自动填充更新时间
///   - 支持可测试性,通过注入 {@link java.time.Clock} 实现时间控制
///   - 预留用户信息填充扩展点(从安全上下文获取当前用户)
///
/// ## 核心组件
///
/// - {@link com.patra.starter.mybatis.handler.AuditMetaObjectHandler} - 审计字段自动填充处理器
///
/// ## 设计决策
///
/// - **时钟注入:** 接受可选的 {@link java.time.Clock} 参数,支持时间敏感型测试场景
///   - **严格模式:** 使用 `strictInsertFill` 和 `strictUpdateFill` 避免覆盖已设置的值
///   - **扩展性:** 用户信息填充逻辑预留 TODO,未来可集成 Spring Security 上下文
///
/// ## 支持的审计字段
///
/// 配合 {@link com.patra.starter.mybatis.entity.BaseDO} 使用,自动填充以下字段:
///
/// - `createdAt` - 创建时间(插入时填充)
///   - `updatedAt` - 更新时间(插入和更新时填充)
///   - `createdBy` - 创建人 ID(待实现)
///   - `createdByName` - 创建人姓名(待实现)
///   - `updatedBy` - 更新人 ID(待实现)
///   - `updatedByName` - 更新人姓名(待实现)
///
/// ## 使用示例
///
/// **实体类定义:**
///
/// ```java
/// @Data
/// @EqualsAndHashCode(callSuper = true)
/// public class ProvenanceDO extends BaseDO {
///     private String name;
///     private String description;
///     // createdAt 和 updatedAt 由 AuditMetaObjectHandler 自动填充
/// ```
///
/// **测试场景(可控时间):**
///
/// ```java
/// @Configuration
/// public class TestConfig {
///     @Bean
///     public Clock fixedClock() {
///         return Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), ZoneId.of("UTC"));
///
///     @Bean
///     public AuditMetaObjectHandler auditMetaObjectHandler(Clock clock) {
///         return new AuditMetaObjectHandler(clock);
/// ```
///
/// ## 相关模块
///
/// - {@link com.patra.starter.mybatis.entity} - 提供 `BaseDO` 基类
///   - {@link com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig} - 注册处理器 Bean
///
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.mybatis.handler;
