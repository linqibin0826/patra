package com.patra.catalog.infra.adapter.persistence.converter.mapper;

import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.vo.mesh.MeshQualifierId;
import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.adapter.persistence.entity.MeshQualifierEntity;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// MeSH 限定词 JPA 实体转换器。
///
/// **职责**：
///
/// - `MeshQualifierAggregate` ↔ `MeshQualifierEntity` 双向转换
/// - 值对象（`MeshUI`、`MeshQualifierId`）与基本类型的映射
///
/// **JPA 特性**：
///
/// - `treeNumbers` 字段由 Hibernate 6.6 原生支持 JSON 映射（`List<String>` → JSON 列）
/// - 无需手动处理 JSON 序列化/反序列化
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public interface MeshQualifierJpaMapper {

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 限定词聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "idToLong")
  @Mapping(target = "ui", source = "qualifierUi", qualifiedByName = "meshUiToString")
  @Mapping(target = "treeNumbers", source = "treeNumbers", qualifiedByName = "copyTreeNumbers")
  @Mapping(target = "version", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdByName", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedByName", ignore = true)
  @Mapping(target = "ipAddress", ignore = true)
  @Mapping(target = "recordRemarks", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  MeshQualifierEntity toEntity(MeshQualifierAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `MeshQualifierAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 限定词聚合根
  default MeshQualifierAggregate toAggregate(MeshQualifierEntity entity) {
    if (entity == null) {
      return null;
    }

    return MeshQualifierAggregate.restore(
        MeshQualifierId.of(entity.getId()),
        MeshUI.of(entity.getUi()),
        entity.getName(),
        entity.getAbbreviation(),
        entity.getAnnotation(),
        entity.getDateCreated(),
        entity.getDateRevised(),
        entity.getDateEstablished(),
        entity.getActiveStatus(),
        entity.getMeshVersion(),
        entity.getHistoryNote(),
        entity.getOnlineNote(),
        entity.getTreeNumbers() != null
            ? new ArrayList<>(entity.getTreeNumbers())
            : new ArrayList<>());
  }

  /// 将 MeshQualifierId 转换为 Long。
  @Named("idToLong")
  default Long idToLong(MeshQualifierId id) {
    return id != null ? id.value() : null;
  }

  /// 将 MeshUI 转换为 String。
  @Named("meshUiToString")
  default String meshUiToString(MeshUI meshUi) {
    return meshUi != null ? meshUi.ui() : null;
  }

  /// 复制 treeNumbers 列表（防止共享引用）。
  @Named("copyTreeNumbers")
  default List<String> copyTreeNumbers(List<String> treeNumbers) {
    return treeNumbers != null ? new ArrayList<>(treeNumbers) : new ArrayList<>();
  }
}
