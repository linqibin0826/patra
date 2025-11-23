/// 仓储实现包。
///
/// 实现 Domain 层定义的 Repository Port 接口，使用 MyBatis-Plus 提供数据持久化能力。
///
/// ## 职责
///
/// - **实现 Repository Port**：实现 Domain 层定义的仓储接口契约
///   - **聚合根持久化**：保存和查询聚合根及其关联对象
///   - **对象转换**：使用 Converter 在 Domain 对象和 DO 对象之间转换
///   - **批量操作**：支持批量插入、更新、删除操作
///   - **流式查询**：支持 Stream API 查询大数据量（避免内存溢出）
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.persistence.repository.MeshImportRepositoryImpl} - MeSH 导入任务仓储实现
///
/// - 实现 {@link com.patra.catalog.domain.port.MeshImportRepository}
///       - 保存/查询 {@link com.patra.catalog.domain.model.aggregate.MeshImportAggregate}
///       - 支持断点续传（幂等性保存）
///       - 查询运行中任务
///   - {@link com.patra.catalog.infra.persistence.repository.MeshDescriptorRepositoryImpl} - MeSH
/// 主题词仓储实现
///
/// - 实现 {@link com.patra.catalog.domain.port.MeshDescriptorRepository}
///       - 批量保存主题词及其关联数据（TreeNumber、EntryTerm、Concept）
///       - 流式查询主题词（返回 Stream）
///   - {@link com.patra.catalog.infra.persistence.repository.MeshBatchDetailRepositoryImpl} - MeSH
/// 批次详情仓储实现
///
/// - 实现 {@link com.patra.catalog.domain.port.MeshBatchDetailRepository}
///       - 保存失败批次详情
///       - 查询失败批次列表
///
/// ## 设计原则
///
/// - **依赖倒置**：Repository 实现依赖 Domain 层定义的 Port 接口
///   - **聚合完整性**：保存/查询聚合根时，同时处理关联对象
///   - **幂等性**：使用 `insertOrUpdate` 实现幂等保存，支持断点续传
///   - **事务边界**：Repository 方法不声明 `@Transactional`，由 App 层统一管理
///   - **DO 封装**：DO 对象不暴露到外层，仅在 Repository 内部使用
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：保存聚合根（含关联对象）
/// @Repository
/// @RequiredArgsConstructor
/// public class MeshImportRepositoryImpl implements MeshImportRepository {
///
///     private final MeshImportTaskMapper taskMapper;
///     private final MeshTableProgressMapper progressMapper;
///     private final MeshImportConverter converter;
///
///     @Override
///     public MeshImportAggregate save(MeshImportAggregate aggregate) {
///         // 1. 转换为 DO 对象
///         MeshImportTaskDO taskDO = converter.toTaskDO(aggregate);
///         List<MeshTableProgressDO> progressDOList = converter.toProgressDOList(aggregate);
///
///         // 2. 保存任务主表（新增或更新）
///         if (taskDO.getId() == null) {
///             taskMapper.insert(taskDO);
///         } else {
///             taskMapper.updateById(taskDO);
///         }
///
///         // 3. 保存表进度（幂等性）
///         progressDOList.forEach(progress -> {
///             progress.setImportId(taskDO.getId());
///             progressMapper.insertOrUpdate(progress); // 使用 ON DUPLICATE KEY UPDATE
///         });
///
///         // 4. 转换回领域对象
///         return converter.toDomain(taskDO, progressDOList);
///     }
/// }
///
/// // 示例 2：查询聚合根（含关联对象）
/// @Override
/// public Optional<MeshImportAggregate> findById(MeshImportId id) {
///     // 1. 查询任务主表
///     MeshImportTaskDO taskDO = taskMapper.selectById(id.value());
///     if (taskDO == null) {
///         return Optional.empty();
///     }
///
///     // 2. 查询表进度列表
///     List<MeshTableProgressDO> progressDOList = progressMapper.findByImportId(taskDO.getId());
///
///     // 3. 转换为领域对象
///     MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);
///
///     return Optional.of(aggregate);
/// }
///
/// // 示例 3：批量保存（优化性能）
/// @Override
/// public void saveBatch(List<MeshDescriptorAggregate> aggregates) {
///     // 1. 转换为 DO 列表
///     List<MeshDescriptorDO> doList = aggregates.stream()
///         .map(converter::toDescriptorDO)
///         .toList();
///
///     // 2. 批量插入（使用 MyBatis-Plus saveBatch）
///     descriptorMapper.insertBatch(doList, 1000); // 每批 1000 条
/// }
///
/// // 示例 4：流式查询（大数据量）
/// @Override
/// public Stream<MeshDescriptorAggregate> findAllStream() {
///     // 使用 MyBatis-Plus 流式查询
///     return descriptorMapper.selectStream(new LambdaQueryWrapper<>())
///         .map(converter::toDomain);
/// }
/// ```
///
/// ## 幂等性设计
///
/// ### 问题：断点续传需要幂等性保存
///
/// **场景**：MeSH 导入任务中断后重试，需要从断点继续，避免重复保存
///
/// **解决方案**：使用 `insertOrUpdate` 实现幂等性
///
/// ```xml
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
/// ```
///
/// **关键点**：
/// - 使用 `ON DUPLICATE KEY UPDATE` 实现 MySQL 幂等性
/// - 仅更新变化的字段（status、processed_count、last_batch_num）
/// - 自动更新 `updated_at` 时间戳
///
/// ## 批量操作优化
///
/// ### 问题：MeSH 导入涉及大量数据（~35 万条），逐条插入性能差
///
/// **解决方案**：使用批量插入优化性能
///
/// ```java
/// @Override
/// public void saveTreeNumbersBatch(List<MeshTreeNumber> treeNumbers) {
///     // 1. 转换为 DO 列表
///     List<MeshTreeNumberDO> doList = treeNumbers.stream()
///         .map(converter::toTreeNumberDO)
///         .toList();
///
///     // 2. 批量插入（每批 2000 条）
///     int batchSize = 2000;
///     for (int i = 0; i < doList.size(); i += batchSize) {
///         int end = Math.min(i + batchSize, doList.size());
///         List<MeshTreeNumberDO> batch = doList.subList(i, end);
///         treeNumberMapper.insertBatch(batch);
///     }
/// }
/// ```
///
/// **性能对比**：
/// - 逐条插入：~35,000 条耗时 ~10 分钟
/// - 批量插入（2000/批）：~35,000 条耗时 ~30 秒
///
/// ## 流式查询
///
/// ### 问题：一次性加载所有数据导致内存溢出
///
/// **解决方案**：使用 MyBatis-Plus 流式查询
///
/// ```java
/// @Override
/// public Stream<MeshDescriptorAggregate> findAllStream() {
///     // MyBatis-Plus 流式查询（游标方式）
///     return descriptorMapper.selectStream(new LambdaQueryWrapper<>())
///         .map(converter::toDomain);
/// }
/// ```
///
/// **使用注意**：
/// - 必须在事务内使用（否则连接会关闭）
/// - 使用 `try-with-resources` 确保资源释放
/// - 避免在 Stream 中执行耗时操作
///
/// ## 依赖关系
///
/// - **上游依赖**：
///   - `patra-catalog-domain`（Domain 层 Port 接口）
///   - `patra-spring-boot-starter-mybatis`（MyBatis-Plus 自动配置）
///   - `MeshImportConverter`（对象转换器）
/// - **下游依赖**：
///   - `MeshImportTaskMapper`（MyBatis-Plus Mapper）
///   - `MeshTableProgressMapper`（MyBatis-Plus Mapper）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.persistence.repository;
