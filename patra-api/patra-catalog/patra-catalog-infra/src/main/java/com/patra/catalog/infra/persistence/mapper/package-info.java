/// MyBatis-Plus Mapper 接口包。
///
/// 定义数据库访问接口，使用 MyBatis-Plus 提供基础 CRUD 和高级查询能力。
///
/// ## 职责
///
/// - **基础 CRUD**：继承 {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} 获得基础 CRUD 方法
///   - **自定义查询**：使用 {@link com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper}
/// 构建类型安全查询
///   - **批量操作**：自定义批量插入/更新方法（XML 实现）
///   - **流式查询**：使用 `selectStream` 实现大数据量查询
///   - **幂等操作**：使用 `ON DUPLICATE KEY UPDATE` 实现幂等性插入/更新
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.persistence.mapper.MeshImportTaskMapper} - MeSH 导入任务表 Mapper
///
/// - 基础 CRUD：`insert`、`updateById`、`selectById`、`deleteById`
///       - 自定义查询：查询运行中任务（`findRunningTask`）
///   - {@link com.patra.catalog.infra.persistence.mapper.MeshTableProgressMapper} - MeSH 表进度表 Mapper
///
/// - 幂等性插入/更新：`insertOrUpdate`（使用 XML 实现）
///       - 按 `importId` 查询：`findByImportId`
///   - {@link com.patra.catalog.infra.persistence.mapper.MeshDescriptorMapper} - MeSH 主题词表 Mapper
///
/// - 批量插入：`insertBatch`（XML 实现）
///       - 流式查询：`selectStream`（避免内存溢出）
///   - {@link com.patra.catalog.infra.persistence.mapper.MeshBatchDetailMapper} - MeSH 批次详情表 Mapper
///
/// - 查询失败批次：`findFailedBatches`
///
/// ## 设计原则
///
/// - **禁止使用 @Select 等注解**：简单查询使用 LambdaQueryWrapper，复杂查询使用 XML（遵循 best-practices.md 规则 2）
///   - **类型安全查询**：使用 `LambdaQueryWrapper` 避免硬编码字段名
///   - **批量操作优化**：自定义批量插入方法，提升性能
///   - **流式查询**：使用 `selectStream` 处理大数据量，避免 OOM
///   - **幂等性设计**：使用 `ON DUPLICATE KEY UPDATE` 实现幂等性
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：继承 BaseMapper 获得基础 CRUD
/// @Mapper
/// public interface MeshImportTaskMapper extends BaseMapper<MeshImportTaskDO> {
///
///     // BaseMapper 提供的方法：
///     // - int insert(T entity)
///     // - int updateById(T entity)
///     // - T selectById(Serializable id)
///     // - int deleteById(Serializable id)
///     // - List<T> selectList(Wrapper<T> queryWrapper)
///     // - Long selectCount(Wrapper<T> queryWrapper)
/// }
///
/// // 示例 2：使用 LambdaQueryWrapper 构建类型安全查询
/// LambdaQueryWrapper<MeshImportTaskDO> wrapper = new LambdaQueryWrapper<>();
/// wrapper.in(MeshImportTaskDO::getStatus, "PENDING", "PROCESSING")
///     .orderByDesc(MeshImportTaskDO::getCreatedAt)
///     .last("LIMIT 1");
///
/// MeshImportTaskDO task = meshImportTaskMapper.selectOne(wrapper);
///
/// // 示例 3：自定义批量插入方法（XML 实现）
/// @Mapper
/// public interface MeshDescriptorMapper extends BaseMapper<MeshDescriptorDO> {
///
///     /// 批量插入主题词
///     void insertBatch(@Param("list") List<MeshDescriptorDO> descriptors);
/// }
///
/// <!-- MeshDescriptorMapper.xml -->
/// <insert id="insertBatch" parameterType="java.util.List">
///     INSERT INTO cat_mesh_descriptor (
///         descriptor_ui, descriptor_name, descriptor_class, date_created
///     ) VALUES
///     <foreach collection="list" item="item" separator=",">
///         (#{item.descriptorUI}, #{item.descriptorName}, #{item.descriptorClass}, #{item.dateCreated})
///     </foreach>
/// </insert>
///
/// // 示例 4：幂等性插入/更新（ON DUPLICATE KEY UPDATE）
/// @Mapper
/// public interface MeshTableProgressMapper extends BaseMapper<MeshTableProgressDO> {
///
///     /// 插入或更新表进度（幂等操作）
///     void insertOrUpdate(MeshTableProgressDO progress);
/// }
///
/// <!-- MeshTableProgressMapper.xml -->
/// <insert id="insertOrUpdate" parameterType="MeshTableProgressDO">
///     INSERT INTO cat_mesh_table_progress (
///         import_id, table_name, status, total_count, processed_count, last_batch_num
///     ) VALUES (
///         #{importId}, #{tableName}, #{status}, #{totalCount}, #{processedCount}, #{lastBatchNum}
///     )
///     ON DUPLICATE KEY UPDATE
///         status = VALUES(status),
///         processed_count = VALUES(processed_count),
///         last_batch_num = VALUES(last_batch_num),
///         updated_at = CURRENT_TIMESTAMP(6)
/// </insert>
///
/// // 示例 5：流式查询（大数据量）
/// @Mapper
/// public interface MeshDescriptorMapper extends BaseMapper<MeshDescriptorDO> {
///     // 使用 BaseMapper 的 selectStream 方法
/// }
///
/// // 使用流式查询
/// try (Stream<MeshDescriptorDO> stream = meshDescriptorMapper.selectStream(new LambdaQueryWrapper<>())) {
///     stream.forEach(descriptor -> {
///         // 处理每条记录
///     });
/// }
/// ```
///
/// ## 批量操作优化
///
/// ### 问题：逐条插入性能差
///
/// **场景**：MeSH 导入涉及 ~35 万条数据，逐条插入耗时 ~10 分钟
///
/// **解决方案**：使用批量插入（XML 实现）
///
/// ```xml
/// <!-- MeshDescriptorMapper.xml -->
/// <insert id="insertBatch" parameterType="java.util.List">
///     INSERT INTO cat_mesh_descriptor (
///         descriptor_ui, descriptor_name, descriptor_class
///     ) VALUES
///     <foreach collection="list" item="item" separator=",">
///         (#{item.descriptorUI}, #{item.descriptorName}, #{item.descriptorClass})
///     </foreach>
/// </insert>
/// ```
///
/// **性能对比**：
/// - 逐条插入：~35,000 条耗时 ~10 分钟
/// - 批量插入（1000/批）：~35,000 条耗时 ~30 秒
///
/// ## 流式查询
///
/// ### 问题：一次性加载所有数据导致内存溢出
///
/// **解决方案**：使用 MyBatis-Plus 的 `selectStream` 方法
///
/// ```java
/// try (Stream<MeshDescriptorDO> stream = descriptorMapper.selectStream(new LambdaQueryWrapper<>())) {
///     stream.forEach(descriptor -> {
///         // 处理每条记录（逐条加载，内存占用小）
///     });
/// }
/// ```
///
/// **注意事项**：
/// - 必须在事务内使用（`@Transactional`）
/// - 使用 `try-with-resources` 确保 Stream 关闭
/// - 避免在 Stream 中执行耗时操作（会长时间占用数据库连接）
///
/// ## 幂等性设计
///
/// ### 问题：断点续传需要幂等性保存
///
/// **场景**：MeSH 导入任务中断后重试，需要从断点继续，避免重复保存
///
/// **解决方案**：使用 `ON DUPLICATE KEY UPDATE` 实现幂等性
///
/// ```xml
/// <insert id="insertOrUpdate" parameterType="MeshTableProgressDO">
///     INSERT INTO cat_mesh_table_progress (
///         import_id, table_name, status, processed_count, last_batch_num
///     ) VALUES (
///         #{importId}, #{tableName}, #{status}, #{processedCount}, #{lastBatchNum}
///     )
///     ON DUPLICATE KEY UPDATE
///         status = VALUES(status),
///         processed_count = VALUES(processed_count),
///         last_batch_num = VALUES(last_batch_num),
///         updated_at = CURRENT_TIMESTAMP(6)
/// </insert>
/// ```
///
/// **关键点**：
/// - 使用唯一索引（`import_id`, `table_name`）作为冲突检测条件
/// - 仅更新变化的字段（status、processed_count、last_batch_num）
/// - 自动更新 `updated_at` 时间戳
///
/// ## LambdaQueryWrapper 类型安全查询
///
/// ### 传统方式（硬编码字段名，易出错）
///
/// ```java
/// QueryWrapper<MeshImportTaskDO> wrapper = new QueryWrapper<>();
/// wrapper.in("status", "PENDING", "PROCESSING"); // 硬编码字段名，易拼写错误
/// ```
///
/// ### 推荐方式（类型安全，编译期检查）
///
/// ```java
/// LambdaQueryWrapper<MeshImportTaskDO> wrapper = new LambdaQueryWrapper<>();
/// wrapper.in(MeshImportTaskDO::getStatus, "PENDING", "PROCESSING"); // 编译期检查，避免拼写错误
///     .eq(MeshImportTaskDO::getDeleted, 0)
///     .orderByDesc(MeshImportTaskDO::getCreatedAt);
/// ```
///
/// **优势**：
/// - 编译期检查字段名，避免拼写错误
/// - 支持 IDE 自动补全和重构
/// - 类型安全，防止字段类型不匹配
///
/// ## 依赖关系
///
/// - **上游依赖**：
///   - `patra-spring-boot-starter-mybatis`（MyBatis-Plus 自动配置）
///   - `MeshImportTaskDO`（实体类）
/// - **下游依赖**：
///   - `MeshImportRepositoryImpl`（Repository 实现）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.persistence.mapper;
