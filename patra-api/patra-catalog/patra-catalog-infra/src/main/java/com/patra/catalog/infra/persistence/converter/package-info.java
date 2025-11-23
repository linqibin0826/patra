/// 对象转换器包。
///
/// 使用 MapStruct 实现 Domain 对象与 DO（数据库实体）对象之间的双向转换。
///
/// ## 职责
///
/// - **双向转换**：Domain → DO（持久化场景）和 DO → Domain（查询场景）
///   - **强类型 ID 转换**：处理强类型 ID（如 MeshImportId）与数据库 Long 类型的互转
///   - **枚举转换**：处理领域枚举与数据库字符串/整数的互转
///   - **集合转换**：处理列表、Set 等集合类型的转换
///   - **复杂对象转换**：处理聚合根、实体、值对象的完整转换
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.infra.persistence.converter.MeshImportConverter} - MeSH 导入任务转换器
///
/// - 转换 {@link com.patra.catalog.domain.model.aggregate.MeshImportAggregate} ↔ {@link
/// com.patra.catalog.infra.persistence.entity.MeshImportTaskDO}
///       - 处理 {@link com.patra.catalog.domain.model.valueobject.MeshImportId} ↔ Long
///       - 处理 {@link com.patra.catalog.domain.model.valueobject.TableProgress} ↔ {@link
/// com.patra.catalog.infra.persistence.entity.MeshTableProgressDO}
///       - 处理枚举状态转换（MeshImportTaskStatus ↔ String）
///   - {@link com.patra.catalog.infra.persistence.converter.MeshDescriptorConverter} - MeSH
/// 主题词转换器（规划中）
///   - {@link com.patra.catalog.infra.persistence.converter.PublicationConverter} - 文献转换器（规划中）
///
/// ## 设计原则
///
/// - **零运行时开销**：MapStruct 在编译期生成转换代码，运行时无反射或性能损失
///   - **类型安全**：编译期检查类型匹配，防止运行时类型错误
///   - **显式映射**：复杂字段使用 `@Mapping` 显式声明映射关系
///   - **自定义转换**：使用 `@Named` 方法处理特殊转换逻辑（如强类型 ID）
///   - **Spring 集成**：使用 `componentModel = "spring"` 自动注册为 Spring Bean
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：DO → Domain（查询场景）
/// @Repository
/// @RequiredArgsConstructor
/// public class MeshImportRepositoryImpl implements MeshImportRepository {
///
///     private final MeshImportTaskMapper taskMapper;
///     private final MeshTableProgressMapper progressMapper;
///     private final MeshImportConverter converter;
///
///     @Override
///     public Optional<MeshImportAggregate> findById(MeshImportId id) {
///         // 1. 查询数据库
///         MeshImportTaskDO taskDO = taskMapper.selectById(id.value());
///         List<MeshTableProgressDO> progressDOList = progressMapper.findByImportId(taskDO.getId());
///
///         // 2. 转换为领域对象
///         MeshImportAggregate aggregate = converter.toDomain(taskDO, progressDOList);
///
///         return Optional.of(aggregate);
///     }
/// }
///
/// // 示例 2：Domain → DO（持久化场景）
/// @Override
/// public MeshImportAggregate save(MeshImportAggregate aggregate) {
///     // 1. 转换为 DO 对象
///     MeshImportTaskDO taskDO = converter.toTaskDO(aggregate);
///     List<MeshTableProgressDO> progressDOList = converter.toProgressDOList(aggregate);
///
///     // 2. 保存到数据库
///     if (taskDO.getId() == null) {
///         taskMapper.insert(taskDO);
///     } else {
///         taskMapper.updateById(taskDO);
///     }
///
///     // 3. 保存关联对象
///     progressDOList.forEach(progress -> {
///         progress.setImportId(taskDO.getId());
///         progressMapper.insertOrUpdate(progress);
///     });
///
///     // 4. 转换回领域对象
///     return converter.toDomain(taskDO, progressDOList);
/// }
///
/// // 示例 3：自定义强类型 ID 转换
/// @Mapper(componentModel = "spring")
/// public interface MeshImportConverter {
///
///     // Long → MeshImportId
///     @Named("toMeshImportId")
///     default MeshImportId toMeshImportId(Long id) {
///         return id != null ? MeshImportId.of(id) : null;
///     }
///
///     // MeshImportId → Long
///     @Named("toLong")
///     default Long toLong(MeshImportId id) {
///         return id != null ? id.value() : null;
///     }
///
///     // 使用自定义转换方法
///     @Mapping(target = "id", source = "taskDO.id", qualifiedByName = "toMeshImportId")
///     MeshImportAggregate toDomain(MeshImportTaskDO taskDO, List<MeshTableProgressDO> progressDOList);
/// }
/// ```
///
/// ## 转换策略
///
/// ### 1. 聚合根转换
///
/// **策略**：聚合根转换需要同时处理主对象和关联对象
///
/// - **MeshImportAggregate**：
///   - 主对象：MeshImportTaskDO（任务主表）
///   - 关联对象：List&lt;MeshTableProgressDO&gt;（表进度列表）
///   - 强类型 ID：MeshImportId ↔ Long
///
/// ### 2. 值对象转换
///
/// **策略**：值对象转换需要处理不可变性
///
/// - **TableProgress**：
///   - 使用 `toBuilder()` 方法实现不可变更新
///   - 转换时使用 `@Mapping` 映射字段名差异（expectedCount ↔ totalCount）
///
/// ### 3. 枚举转换
///
/// **策略**：枚举与数据库字符串/整数互转
///
/// - **MeshImportTaskStatus**：
///   - Domain 枚举 → 数据库字符串（name()）
///   - 数据库字符串 → Domain 枚举（valueOf()）
///
/// ## MapStruct 配置
///
/// ```java
/// @Mapper(
///     componentModel = "spring",         // Spring Bean 自动注入
///     unmappedTargetPolicy = ReportingPolicy.ERROR  // 未映射字段报错（严格模式）
/// )
/// public interface MeshImportConverter {
///     // 转换方法...
/// }
/// ```
///
/// ## 注意事项
///
/// 1. **DO 对象不泄露**：DO 对象仅在 Infrastructure 层内部使用，不暴露到 App/Adapter 层
/// 2. **审计字段忽略**：转换时使用 `ignore = true` 忽略审计字段（createdAt、updatedAt 等）
/// 3. **集合转换性能**：使用 Stream API 转换集合，避免多次遍历
/// 4. **null 安全**：转换方法内部检查 null，避免 NullPointerException
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.infra.persistence.converter;
