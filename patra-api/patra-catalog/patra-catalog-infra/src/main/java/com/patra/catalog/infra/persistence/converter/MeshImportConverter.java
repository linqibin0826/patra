package com.patra.catalog.infra.persistence.converter;

import com.patra.catalog.domain.model.aggregate.MeshImportAggregate;
import com.patra.catalog.domain.model.enums.MeshImportTaskStatus;
import com.patra.catalog.domain.model.enums.MeshTableImportStatus;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import com.patra.catalog.domain.model.valueobject.TableProgress;
import com.patra.catalog.infra.persistence.entity.MeshImportTaskDO;
import com.patra.catalog.infra.persistence.entity.MeshTableProgressDO;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// MeSH 导入转换器接口。
///
/// 使用 MapStruct 进行 Domain 对象与 DO 对象之间的转换。
///
/// **设计原则**：
///
/// - 双向转换：支持 Domain → DO 和 DO → Domain
///   - 强类型 ID：处理 MeshImportId 与 Long 的互转
///   - 枚举转换：处理枚举与字符串的互转
///   - 集合转换：处理 List<TableProgress> 与 List<MeshTableProgressDO> 的互转
///   - Spring 集成：使用 `componentModel = "spring"` 注入到 Spring 容器
///
/// **转换方法**：
///
/// - {@link #toDomain(MeshImportTaskDO, List)} - 转换为聚合根
///   - {@link #toTaskDO(MeshImportAggregate)} - 转换为 TaskDO
///   - {@link #toProgressDOList(MeshImportAggregate)} - 转换为 ProgressDO 列表
///   - {@link #toTableProgress(MeshTableProgressDO)} - 转换为 TableProgress
///   - {@link #toProgressDO(TableProgress)} - 转换为 ProgressDO
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public interface MeshImportConverter {

  /// 转换为领域聚合根。
  ///
  /// 从 TaskDO 和 ProgressDO 列表重建聚合根。
  ///
  /// @param taskDO 任务 DO
  /// @param progressDOList 表进度 DO 列表
  /// @return 聚合根
  @Mapping(target = "id", expression = "java(toMeshImportId(taskDO.getId()))")
  @Mapping(target = "status", expression = "java(toTaskStatus(taskDO.getStatus()))")
  @Mapping(target = "tableProgressList", expression = "java(toTableProgressList(progressDOList))")
  @Mapping(target = "domainEvents", ignore = true)
  MeshImportAggregate toDomain(MeshImportTaskDO taskDO, List<MeshTableProgressDO> progressDOList);

  /// 转换为 TaskDO。
  ///
  /// 从聚合根提取任务主表数据。
  ///
  /// @param aggregate 聚合根
  /// @return TaskDO
  @Mapping(target = "id", expression = "java(toLong(aggregate.getId()))")
  @Mapping(target = "status", expression = "java(fromTaskStatus(aggregate.getStatus()))")
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  MeshImportTaskDO toTaskDO(MeshImportAggregate aggregate);

  /// 转换为 ProgressDO 列表。
  ///
  /// 从聚合根提取表进度列表。
  ///
  /// @param aggregate 聚合根
  /// @return ProgressDO 列表
  default List<MeshTableProgressDO> toProgressDOList(MeshImportAggregate aggregate) {
    Long importId = toLong(aggregate.getId());
    return aggregate.getTableProgressList().stream()
        .map(
            progress -> {
              MeshTableProgressDO progressDO = toProgressDO(progress);
              progressDO.setImportId(importId);
              return progressDO;
            })
        .collect(Collectors.toList());
  }

  /// 转换为 TableProgress。
  ///
  /// 从 ProgressDO 转换为值对象。
  ///
  /// @param progressDO ProgressDO
  /// @return TableProgress
  @Mapping(target = "status", expression = "java(toTableStatus(progressDO.getStatus()))")
  @Mapping(target = "lastUpdateTime", expression = "java(progressDO.getUpdatedAt())")
  TableProgress toTableProgress(MeshTableProgressDO progressDO);

  /// 转换为 ProgressDO。
  ///
  /// 从 TableProgress 转换为 DO。
  ///
  /// @param progress TableProgress
  /// @return ProgressDO
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "importId", ignore = true)
  @Mapping(target = "status", expression = "java(fromTableStatus(progress.getStatus()))")
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  MeshTableProgressDO toProgressDO(TableProgress progress);

  // ========== 辅助方法：ID 转换 ==========

  /// Long → MeshImportId。
  ///
  /// @param id Long ID
  /// @return MeshImportId
  @Named("toMeshImportId")
  default MeshImportId toMeshImportId(Long id) {
    return id != null ? MeshImportId.of(id) : null;
  }

  /// MeshImportId → Long。
  ///
  /// @param id MeshImportId
  /// @return Long ID
  @Named("toLong")
  default Long toLong(MeshImportId id) {
    return id != null ? id.value() : null;
  }

  // ========== 辅助方法：任务状态转换 ==========

  /// String → MeshImportTaskStatus。
  ///
  /// @param status 状态字符串
  /// @return 任务状态枚举
  @Named("toTaskStatus")
  default MeshImportTaskStatus toTaskStatus(String status) {
    return status != null ? MeshImportTaskStatus.valueOf(status) : null;
  }

  /// MeshImportTaskStatus → String。
  ///
  /// @param status 任务状态枚举
  /// @return 状态字符串
  @Named("fromTaskStatus")
  default String fromTaskStatus(MeshImportTaskStatus status) {
    return status != null ? status.name() : null;
  }

  // ========== 辅助方法：表状态转换 ==========

  /// String → MeshTableImportStatus。
  ///
  /// @param status 状态字符串
  /// @return 表状态枚举
  @Named("toTableStatus")
  default MeshTableImportStatus toTableStatus(String status) {
    return status != null ? MeshTableImportStatus.valueOf(status) : null;
  }

  /// MeshTableImportStatus → String。
  ///
  /// @param status 表状态枚举
  /// @return 状态字符串
  @Named("fromTableStatus")
  default String fromTableStatus(MeshTableImportStatus status) {
    return status != null ? status.name() : null;
  }

  // ========== 辅助方法：集合转换 ==========

  /// List<MeshTableProgressDO> → List<TableProgress>。
  ///
  /// @param progressDOList ProgressDO 列表
  /// @return TableProgress 列表
  @Named("toTableProgressList")
  default List<TableProgress> toTableProgressList(List<MeshTableProgressDO> progressDOList) {
    if (progressDOList == null) {
      return List.of();
    }
    return progressDOList.stream().map(this::toTableProgress).collect(Collectors.toList());
  }
}
