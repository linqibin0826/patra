/// 数据库实体（DO）包。
///
/// 包含所有数据库表的实体类（Data Object），使用 MyBatis-Plus 进行 ORM 映射。
///
/// ## 职责
///
/// - **数据库映射**：与数据库表一对一映射，使用 `@TableName` 指定表名
///   - **字段映射**：与数据库列一对一映射，使用 `@TableField` 自定义映射规则
///   - **审计字段**：继承 {@link com.patra.common.mybatisplus.BaseDO}，自动填充创建/更新时间和操作人
///   - **主键策略**：使用 `@TableId(type = IdType.AUTO)` 自动生成主键
///   - **逻辑删除**：使用 `@TableLogic` 实现软删除
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.persistence.entity.MeshImportTaskDO} - MeSH 导入任务主表实体
///
/// - 对应表：`cat_mesh_import_task`
///       - 存储任务元数据（任务名称、状态、数据源 URL、总记录数等）
///       - 与 {@link com.patra.catalog.infra.persistence.entity.MeshTableProgressDO} 一对多关系
///   - {@link com.patra.catalog.infra.persistence.entity.MeshTableProgressDO} - MeSH 表进度实体
///
/// - 对应表：`cat_mesh_table_progress`
///       - 存储各表的导入进度（已处理数、状态、最后批次号）
///       - 支持断点续传（记录 `last_batch_num`）
///   - {@link com.patra.catalog.infra.persistence.entity.MeshBatchDetailDO} - MeSH 批次详情实体
///
/// - 对应表：`cat_mesh_batch_detail`
///       - 存储每个批次的详细信息（批次号、成功/失败数、失败原因）
///       - 用于失败批次追踪和重试
///   - {@link com.patra.catalog.infra.persistence.entity.MeshDescriptorDO} - MeSH 主题词实体
///
/// - 对应表：`cat_mesh_descriptor`
///       - 存储 MeSH 主题词基本信息
///   - {@link com.patra.catalog.infra.persistence.entity.MeshQualifierDO} - MeSH 限定词实体
///
/// - 对应表：`cat_mesh_qualifier`
///       - 存储 MeSH 限定词基本信息
///
/// ## 设计原则
///
/// - **DO 不泄露**：DO 对象仅在 Infrastructure 层内部使用，不暴露到 App/Domain 层
///   - **继承 BaseDO**：所有 DO 继承 {@link com.patra.common.mybatisplus.BaseDO}，自动获得审计字段
///   - **Lombok 简化**：使用 `@Data` 自动生成 getter/setter/toString/equals/hashCode
///   - **Builder 模式**：使用 `@Builder` 提供链式构造方法（测试场景）
///   - **驼峰命名**：Java 字段使用驼峰命名，数据库列使用下划线命名（自动映射）
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：定义 DO 实体类
/// @Data
/// @EqualsAndHashCode(callSuper = true)
/// @TableName("cat_mesh_import_task")
/// public class MeshImportTaskDO extends BaseDO {
///
///     /// 任务 ID（主键，自增）
///     @TableId(type = IdType.AUTO)
///     private Long id;
///
///     /// 任务名称
///     @TableField("task_name")
///     private String taskName;
///
///     /// 任务状态（PENDING/PROCESSING/SUCCESS/FAILED）
///     @TableField("status")
///     private String status;
///
///     /// 数据源 URL
///     @TableField("descriptor_source_url")
///     private String descriptorSourceUrl;
///
///     /// 总记录数
///     @TableField("total_records")
///     private Integer totalRecords;
///
///     /// 已处理记录数
///     @TableField("processed_records")
///     private Integer processedRecords;
///
///     // 审计字段由 BaseDO 提供：
///     // - createdAt（创建时间，自动填充）
///     // - createdBy（创建人，自动填充）
///     // - updatedAt（更新时间，自动填充）
///     // - updatedBy（更新人，自动填充）
///     // - version（乐观锁版本号）
///     // - deleted（逻辑删除标记）
/// }
///
/// // 示例 2：使用 Builder 构造 DO（测试场景）
/// MeshImportTaskDO taskDO = MeshImportTaskDO.builder()
///     .taskName("2025年MeSH数据导入")
///     .status("PENDING")
///     .descriptorSourceUrl("https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml")
///     .totalRecords(0)
///     .processedRecords(0)
///     .build();
///
/// // 示例 3：MyBatis-Plus 自动填充审计字段
/// // 插入时自动填充 createdAt、createdBy
/// meshImportTaskMapper.insert(taskDO);
///
/// // 更新时自动填充 updatedAt、updatedBy
/// meshImportTaskMapper.updateById(taskDO);
///
/// // 示例 4：逻辑删除
/// @TableLogic
/// private Integer deleted; // 0=未删除，1=已删除
///
/// // 调用 delete 方法时，实际执行 UPDATE 语句
/// meshImportTaskMapper.deleteById(taskDO.getId());
/// // 生成 SQL: UPDATE cat_mesh_import_task SET deleted = 1 WHERE id = ?
/// ```
///
/// ## 审计字段自动填充
///
/// ### 继承 BaseDO 提供的审计字段
///
/// ```java
/// public abstract class BaseDO implements Serializable {
///
///     /// 创建时间（自动填充）
///     @TableField(fill = FieldFill.INSERT)
///     private Instant createdAt;
///
///     /// 创建人（自动填充）
///     @TableField(fill = FieldFill.INSERT)
///     private Long createdBy;
///
///     /// 更新时间（自动填充）
///     @TableField(fill = FieldFill.INSERT_UPDATE)
///     private Instant updatedAt;
///
///     /// 更新人（自动填充）
///     @TableField(fill = FieldFill.INSERT_UPDATE)
///     private Long updatedBy;
///
///     /// 乐观锁版本号
///     @Version
///     private Integer version;
///
///     /// 逻辑删除标记
///     @TableLogic
///     private Integer deleted;
/// }
/// ```
///
/// **自动填充逻辑**：由 `patra-spring-boot-starter-mybatis` 统一提供
///
/// ## 字段映射规则
///
/// ### 自动映射（驼峰 → 下划线）
///
/// | Java 字段 | 数据库列 |
/// |----------|---------|
/// | taskName | task_name |
/// | descriptorSourceUrl | descriptor_source_url |
/// | totalRecords | total_records |
///
/// ### 自定义映射（使用 @TableField）
///
/// ```java
/// @TableField("custom_column_name")
/// private String customField;
/// ```
///
/// ## 逻辑删除
///
/// ### 使用 @TableLogic 实现软删除
///
/// ```java
/// @TableLogic
/// private Integer deleted; // 0=未删除，1=已删除
/// ```
///
/// **效果**：
/// - `deleteById(id)` → `UPDATE ... SET deleted = 1 WHERE id = ?`
/// - `selectById(id)` → `SELECT ... WHERE id = ? AND deleted = 0`
///
/// ## 乐观锁
///
/// ### 使用 @Version 实现乐观锁
///
/// ```java
/// @Version
/// private Integer version;
/// ```
///
/// **效果**：
/// - `updateById(entity)` → `UPDATE ... SET ..., version = version + 1 WHERE id = ? AND version = ?`
/// - 如果 `version` 不匹配，返回 0（更新失败）
///
/// ## 注意事项
///
/// 1. **DO 对象职责单一**：仅用于数据库映射，不包含业务逻辑
/// 2. **使用 Instant 而非 LocalDateTime**：审计字段统一使用 UTC 时间（Instant）
/// 3. **禁止直接暴露 DO**：DO 对象通过 Converter 转换为 Domain 对象后再返回
/// 4. **JSON 类型字段**：使用 MyBatis-Plus 的 JSON 类型处理器（`patra-spring-boot-starter-mybatis`
/// 提供）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.persistence.entity;
