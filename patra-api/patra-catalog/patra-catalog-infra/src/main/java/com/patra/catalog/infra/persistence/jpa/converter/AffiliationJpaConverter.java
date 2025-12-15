package com.patra.catalog.infra.persistence.jpa.converter;

import com.patra.catalog.domain.model.aggregate.AffiliationAggregate;
import com.patra.catalog.domain.model.vo.affiliation.AffiliationId;
import com.patra.catalog.domain.model.vo.affiliation.GridId;
import com.patra.catalog.domain.model.vo.affiliation.RorId;
import com.patra.catalog.domain.model.vo.common.DedupKey;
import com.patra.catalog.infra.persistence.jpa.entity.AffiliationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// 机构 JPA 实体转换器。
///
/// **职责**：
///
/// - `AffiliationAggregate` ↔ `AffiliationEntity` 双向转换
/// - 值对象（`RorId`、`GridId`、`DedupKey`、`AffiliationId`）与基本类型的映射
/// - `AffiliationType` 枚举直接映射（JPA 自动转换）
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public interface AffiliationJpaConverter {

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 机构聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "affiliationIdToLong")
  @Mapping(target = "rorId", source = "rorId", qualifiedByName = "rorIdToString")
  @Mapping(target = "gridId", source = "gridId", qualifiedByName = "gridIdToString")
  @Mapping(target = "dedupKey", source = "dedupKey", qualifiedByName = "dedupKeyToString")
  @Mapping(target = "metadata", ignore = true) // metadataJson 需要特殊处理
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
  AffiliationEntity toEntity(AffiliationAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `AffiliationAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 机构聚合根
  default AffiliationAggregate toAggregate(AffiliationEntity entity) {
    if (entity == null) {
      return null;
    }

    return AffiliationAggregate.restore(
        entity.getId() != null ? AffiliationId.of(entity.getId()) : null,
        entity.getName(),
        entity.getOriginalName(),
        entity.getDepartment(),
        entity.getDivision(),
        entity.getSection(),
        entity.getCity(),
        entity.getStateProvince(),
        entity.getCountry(),
        entity.getPostalCode(),
        entity.getRorId() != null ? RorId.of(entity.getRorId()) : null,
        entity.getGridId() != null ? GridId.of(entity.getGridId()) : null,
        entity.getIsni(),
        entity.getRinggoldId(),
        entity.getParentAffiliation(),
        entity.getAffiliationType(),
        entity.getDedupKey() != null ? DedupKey.fromHash(entity.getDedupKey()) : null,
        entity.getVersion());
  }

  /// 将 AffiliationId 转换为 Long。
  @Named("affiliationIdToLong")
  default Long affiliationIdToLong(AffiliationId id) {
    return id != null ? id.value() : null;
  }

  /// 将 RorId 转换为 String。
  @Named("rorIdToString")
  default String rorIdToString(RorId rorId) {
    return rorId != null ? rorId.value() : null;
  }

  /// 将 GridId 转换为 String。
  @Named("gridIdToString")
  default String gridIdToString(GridId gridId) {
    return gridId != null ? gridId.value() : null;
  }

  /// 将 DedupKey 转换为 String。
  @Named("dedupKeyToString")
  default String dedupKeyToString(DedupKey dedupKey) {
    return dedupKey != null ? dedupKey.value() : null;
  }
}
