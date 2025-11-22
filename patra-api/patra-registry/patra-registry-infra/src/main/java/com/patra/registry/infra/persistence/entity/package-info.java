/// 数据库实体根包 - 持久化对象定义。
/// 
/// 本包包含所有数据库实体(DO - Data Object),使用 MyBatis-Plus 注解映射数据库表。实体对象遵循持久化关注点,与领域对象分离,通过转换器进行映射。
/// 
/// ## 职责
/// 
/// - 定义数据库表结构的 Java 对象表示
///   - 使用 MyBatis-Plus 注解配置表名、主键、字段映射
///   - 包含审计字段(`createdAt`, `updatedAt`)
///   - 支持逻辑删除、乐观锁等持久化特性
/// 
/// ## 包结构
/// 
/// - `provenance` - 数据源相关实体
///       
/// - `RegProvenanceDO` - 数据源元数据
///         - `RegProvWindowOffsetCfgDO` - 时间窗口偏移配置
///         - `RegProvPaginationCfgDO` - 分页配置
///         - `RegProvHttpCfgDO` - HTTP 配置
///         - `RegProvBatchingCfgDO` - 批处理配置
///         - `RegProvRetryCfgDO` - 重试配置
///         - `RegProvRateLimitCfgDO` - 速率限制配置
/// 
///   - `expr` - 表达式相关实体
///       
/// - `RegExprFieldDictDO` - 表达式字段定义
///         - `RegProvExprCapabilityDO` - 数据源能力定义
///         - `RegProvApiParamMapDO` - API 参数映射
///         - `RegProvExprRenderRuleDO` - 表达式渲染规则
/// 
///   - `dictionary` - 系统字典实体
///       
/// - `RegSysDictTypeDO` - 字典类型
///         - `RegSysDictItemDO` - 字典项
///         - `RegSysDictItemAliasDO` - 字典项别名
/// 
/// ## 命名约定
/// 
/// - 实体类名: `Reg*DO`(Registry Data Object)
///   - 表名前缀: `reg_`
///   - 主键字段: `id`(Long 类型,自增)
///   - 审计字段: `created_at`, `updated_at`
/// 
/// ## MyBatis-Plus 注解
/// 
/// - `@TableName` - 指定表名
///   - `@TableId` - 标记主键字段(默认自增)
///   - `@TableField` - 映射数据库字段名
///   - `@TableLogic` - 标记逻辑删除字段
///   - `@Version` - 标记乐观锁版本字段
/// 
/// ## 实体设计原则
/// 
/// - **DO/VO 分离**: 实体仅用于持久化,不包含业务逻辑
///   - **贫血模型**: 实体是纯数据载体,不包含行为方法
///   - **审计字段**: 所有实体包含 `created_at`, `updated_at`
///   - **时态字段**: 配置实体包含 `effective_from`, `effective_until`
/// 
/// ## 使用示例
/// 
/// ```java
/// @Data
/// @TableName("reg_provenance")
/// public class RegProvenanceDO {
///     @TableId(type = IdType.AUTO)
///     private Long id;
/// 
///     @TableField("provenance_code")
///     private String provenanceCode;
/// 
///     @TableField("provenance_name")
///     private String provenanceName;
/// 
///     @TableField("default_base_url")
///     private String defaultBaseUrl;
/// 
///     @TableField("is_active")
///     private Boolean isActive;
/// 
///     @TableField("created_at")
///     private Instant createdAt;
/// 
///     @TableField("updated_at")
///     private Instant updatedAt;
/// ```
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.registry.infra.persistence.entity;
