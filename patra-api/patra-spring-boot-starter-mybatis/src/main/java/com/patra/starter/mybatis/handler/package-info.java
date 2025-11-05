/**
 * MyBatis 元数据自动填充处理器包。
 *
 * <p>本包提供 MyBatis-Plus 的 {@link com.baomidou.mybatisplus.core.handlers.MetaObjectHandler} 实现,用于在插入和更新操作时自动填充审计字段(如创建时间、更新时间、操作人等)。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>在插入操作时自动填充创建时间和更新时间
 *   <li>在更新操作时自动填充更新时间
 *   <li>支持可测试性,通过注入 {@link java.time.Clock} 实现时间控制
 *   <li>预留用户信息填充扩展点(从安全上下文获取当前用户)
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.mybatis.handler.AuditMetaObjectHandler} - 审计字段自动填充处理器
 * </ul>
 *
 * <h2>设计决策</h2>
 *
 * <ul>
 *   <li><b>时钟注入:</b> 接受可选的 {@link java.time.Clock} 参数,支持时间敏感型测试场景
 *   <li><b>严格模式:</b> 使用 {@code strictInsertFill} 和 {@code strictUpdateFill} 避免覆盖已设置的值
 *   <li><b>扩展性:</b> 用户信息填充逻辑预留 TODO,未来可集成 Spring Security 上下文
 * </ul>
 *
 * <h2>支持的审计字段</h2>
 *
 * <p>配合 {@link com.patra.starter.mybatis.entity.BaseDO} 使用,自动填充以下字段:
 *
 * <ul>
 *   <li>{@code createdAt} - 创建时间(插入时填充)
 *   <li>{@code updatedAt} - 更新时间(插入和更新时填充)
 *   <li>{@code createdBy} - 创建人 ID(待实现)
 *   <li>{@code createdByName} - 创建人姓名(待实现)
 *   <li>{@code updatedBy} - 更新人 ID(待实现)
 *   <li>{@code updatedByName} - 更新人姓名(待实现)
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <p><b>实体类定义:</b>
 * <pre>{@code
 * @Data
 * @EqualsAndHashCode(callSuper = true)
 * public class ProvenanceDO extends BaseDO {
 *     private String name;
 *     private String description;
 *     // createdAt 和 updatedAt 由 AuditMetaObjectHandler 自动填充
 * }
 * }</pre>
 *
 * <p><b>测试场景(可控时间):</b>
 * <pre>{@code
 * @Configuration
 * public class TestConfig {
 *     @Bean
 *     public Clock fixedClock() {
 *         return Clock.fixed(Instant.parse("2025-01-15T10:00:00Z"), ZoneId.of("UTC"));
 *     }
 *
 *     @Bean
 *     public AuditMetaObjectHandler auditMetaObjectHandler(Clock clock) {
 *         return new AuditMetaObjectHandler(clock);
 *     }
 * }
 * }</pre>
 *
 * <h2>相关模块</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.mybatis.entity} - 提供 {@code BaseDO} 基类
 *   <li>{@link com.patra.starter.mybatis.autoconfig.MybatisPluginAutoConfig} - 注册处理器 Bean
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.mybatis.handler;
