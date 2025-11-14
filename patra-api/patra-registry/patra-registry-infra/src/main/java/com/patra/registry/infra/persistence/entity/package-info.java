/**
 * 数据库实体根包 - 持久化对象定义。
 *
 * <p>本包包含所有数据库实体(DO - Data Object),使用 MyBatis-Plus 注解映射数据库表。实体对象遵循持久化关注点,与领域对象分离,通过转换器进行映射。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>定义数据库表结构的 Java 对象表示
 *   <li>使用 MyBatis-Plus 注解配置表名、主键、字段映射
 *   <li>包含审计字段({@code createdAt}, {@code updatedAt})
 *   <li>支持逻辑删除、乐观锁等持久化特性
 * </ul>
 *
 * <h2>包结构</h2>
 *
 * <ul>
 *   <li>{@code provenance} - 数据源相关实体
 *       <ul>
 *         <li>{@code RegProvenanceDO} - 数据源元数据
 *         <li>{@code RegProvWindowOffsetCfgDO} - 时间窗口偏移配置
 *         <li>{@code RegProvPaginationCfgDO} - 分页配置
 *         <li>{@code RegProvHttpCfgDO} - HTTP 配置
 *         <li>{@code RegProvBatchingCfgDO} - 批处理配置
 *         <li>{@code RegProvRetryCfgDO} - 重试配置
 *         <li>{@code RegProvRateLimitCfgDO} - 速率限制配置
 *       </ul>
 *   <li>{@code expr} - 表达式相关实体
 *       <ul>
 *         <li>{@code RegExprFieldDictDO} - 表达式字段定义
 *         <li>{@code RegProvExprCapabilityDO} - 数据源能力定义
 *         <li>{@code RegProvApiParamMapDO} - API 参数映射
 *         <li>{@code RegProvExprRenderRuleDO} - 表达式渲染规则
 *       </ul>
 *   <li>{@code dictionary} - 系统字典实体
 *       <ul>
 *         <li>{@code RegSysDictTypeDO} - 字典类型
 *         <li>{@code RegSysDictItemDO} - 字典项
 *         <li>{@code RegSysDictItemAliasDO} - 字典项别名
 *       </ul>
 * </ul>
 *
 * <h2>命名约定</h2>
 *
 * <ul>
 *   <li>实体类名: {@code Reg*DO}(Registry Data Object)
 *   <li>表名前缀: {@code reg_}
 *   <li>主键字段: {@code id}(Long 类型,自增)
 *   <li>审计字段: {@code created_at}, {@code updated_at}
 * </ul>
 *
 * <h2>MyBatis-Plus 注解</h2>
 *
 * <ul>
 *   <li>{@code @TableName} - 指定表名
 *   <li>{@code @TableId} - 标记主键字段(默认自增)
 *   <li>{@code @TableField} - 映射数据库字段名
 *   <li>{@code @TableLogic} - 标记逻辑删除字段
 *   <li>{@code @Version} - 标记乐观锁版本字段
 * </ul>
 *
 * <h2>实体设计原则</h2>
 *
 * <ul>
 *   <li><b>DO/VO 分离</b>: 实体仅用于持久化,不包含业务逻辑
 *   <li><b>贫血模型</b>: 实体是纯数据载体,不包含行为方法
 *   <li><b>审计字段</b>: 所有实体包含 {@code created_at}, {@code updated_at}
 *   <li><b>时态字段</b>: 配置实体包含 {@code effective_from}, {@code effective_until}
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * @Data
 * @TableName("reg_provenance")
 * public class RegProvenanceDO {
 *     @TableId(type = IdType.AUTO)
 *     private Long id;
 *
 *     @TableField("provenance_code")
 *     private String provenanceCode;
 *
 *     @TableField("provenance_name")
 *     private String provenanceName;
 *
 *     @TableField("default_base_url")
 *     private String defaultBaseUrl;
 *
 *     @TableField("is_active")
 *     private Boolean isActive;
 *
 *     @TableField("created_at")
 *     private Instant createdAt;
 *
 *     @TableField("updated_at")
 *     private Instant updatedAt;
 * }
 * }</pre>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.registry.infra.persistence.entity;
