package com.patra.catalog.infra.persistence.jpa.converter;

import com.patra.catalog.domain.model.aggregate.VenueAggregate;
import com.patra.catalog.domain.model.enums.VenueIdentifierType;
import com.patra.catalog.domain.model.enums.VenueType;
import com.patra.catalog.domain.model.vo.venue.ProvenanceInfo;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueIdentifier;
import com.patra.catalog.infra.persistence.jpa.entity.VenueEntity;
import com.patra.catalog.infra.persistence.jpa.entity.VenueIdentifierEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// 载体 JPA 实体转换器。
///
/// **职责**：
///
/// - `VenueAggregate` ↔ `VenueEntity` 双向转换
/// - `VenueIdentifier` ↔ `VenueIdentifierEntity` 双向转换
/// - 枚举类型（`VenueType`、`VenueIdentifierType`）与 String 的映射
/// - ProvenanceInfo 展开与重建
/// - 快速访问字段的提取与跳过
///
/// **特殊处理**：
///
/// - 快速访问字段（nlmId、issnL、openalexId）从标识符集合提取
/// - 快速访问字段（abbreviatedTitle、primaryLanguage、countryCode）从 publicationProfile 提取
/// - toAggregate 后需手动设置 identifiers、provenance 和嵌入式值对象
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class VenueJpaConverter {

  // ========== 主表转换 ==========

  /// 将聚合根转换为 JPA 实体。
  ///
  /// **注意**：转换后需手动设置快速访问字段。
  ///
  /// @param aggregate 载体聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "venueIdToLong")
  @Mapping(target = "venueType", source = "venueType", qualifiedByName = "venueTypeToString")
  @Mapping(target = "provenanceCode", source = "provenance.code")
  @Mapping(target = "sourceCreatedDate", source = "provenance.sourceCreatedDate")
  @Mapping(target = "sourceUpdatedDate", source = "provenance.sourceUpdatedDate")
  @Mapping(target = "lastSyncedAt", source = "provenance.lastSyncedAt")
  // 快速访问字段需要后处理设置
  @Mapping(target = "nlmId", ignore = true)
  @Mapping(target = "issnL", ignore = true)
  @Mapping(target = "openalexId", ignore = true)
  @Mapping(target = "abbreviatedTitle", source = "publicationProfile.abbreviatedTitle")
  @Mapping(target = "primaryLanguage", source = "publicationProfile.mainLanguage")
  @Mapping(target = "countryCode", source = "publicationProfile.countryCode")
  // 审计字段由 JPA 管理
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
  public abstract VenueEntity toEntity(VenueAggregate aggregate);

  /// 将聚合根转换为 JPA 实体，并设置快速访问字段。
  ///
  /// @param aggregate 载体聚合根
  /// @return JPA 实体（包含快速访问字段）
  public VenueEntity toEntityWithQuickAccessFields(VenueAggregate aggregate) {
    if (aggregate == null) {
      return null;
    }
    VenueEntity entity = toEntity(aggregate);
    populateQuickAccessFields(entity, aggregate);
    return entity;
  }

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `VenueAggregate.restore()` 工厂方法重建聚合根，然后手动设置其他属性。
  ///
  /// @param entity JPA 实体
  /// @return 载体聚合根
  public VenueAggregate toAggregate(VenueEntity entity) {
    if (entity == null) {
      return null;
    }

    // 使用 restore 方法重建聚合根
    VenueAggregate aggregate =
        VenueAggregate.restore(
            entity.getId() != null ? VenueId.of(entity.getId()) : null,
            stringToVenueType(entity.getVenueType()),
            entity.getDisplayName(),
            entity.getVersion());

    // 设置来源信息
    if (entity.getProvenanceCode() != null) {
      ProvenanceInfo provenance =
          ProvenanceInfo.of(
              entity.getProvenanceCode(),
              entity.getSourceCreatedDate(),
              entity.getSourceUpdatedDate(),
              entity.getLastSyncedAt());
      aggregate.withProvenance(provenance);
    }

    // 设置嵌入式值对象
    if (entity.getPublicationProfile() != null) {
      aggregate.withPublicationProfile(entity.getPublicationProfile());
    }
    if (entity.getCitationMetrics() != null) {
      aggregate.withCitationMetrics(entity.getCitationMetrics());
    }
    if (entity.getOpenAccess() != null) {
      aggregate.withOpenAccess(entity.getOpenAccess());
    }
    if (entity.getAffiliatedSocieties() != null && !entity.getAffiliatedSocieties().isEmpty()) {
      aggregate.withAffiliatedSocieties(entity.getAffiliatedSocieties());
    }

    // 清除脏标记（重建不应标记为脏）
    aggregate.clearDirty();

    return aggregate;
  }

  /// 为聚合根添加标识符。
  ///
  /// @param aggregate 聚合根
  /// @param identifierEntities 标识符实体列表
  public void addIdentifiersToAggregate(
      VenueAggregate aggregate, List<VenueIdentifierEntity> identifierEntities) {
    if (aggregate == null || identifierEntities == null) {
      return;
    }

    for (VenueIdentifierEntity entity : identifierEntities) {
      VenueIdentifier identifier = toIdentifier(entity);
      if (identifier != null) {
        aggregate.addIdentifier(identifier);
      }
    }

    // 清除因添加标识符产生的脏标记
    aggregate.clearDirty();
  }

  // ========== 标识符转换 ==========

  /// 将标识符值对象转换为 JPA 实体。
  ///
  /// @param identifier 标识符值对象
  /// @param venueId 载体 ID
  /// @return JPA 实体
  public VenueIdentifierEntity toIdentifierEntity(VenueIdentifier identifier, Long venueId) {
    if (identifier == null) {
      return null;
    }
    VenueIdentifierEntity entity = new VenueIdentifierEntity();
    entity.setVenueId(venueId);
    entity.setIdentifierType(identifier.type().getCode());
    entity.setIdentifierValue(identifier.value());
    entity.setIsPrimary(false); // 默认非首选
    return entity;
  }

  /// 将 JPA 实体转换为标识符值对象。
  ///
  /// @param entity JPA 实体
  /// @return 标识符值对象
  public VenueIdentifier toIdentifier(VenueIdentifierEntity entity) {
    if (entity == null) {
      return null;
    }
    VenueIdentifierType type = stringToIdentifierType(entity.getIdentifierType());
    return new VenueIdentifier(type, entity.getIdentifierValue());
  }

  // ========== 更新方法 ==========

  /// 更新托管实体的可变字段。
  ///
  /// 注意：venueType 和 displayName 是不可变的，不在此更新。
  ///
  /// @param entity 托管实体
  /// @param aggregate 包含更新数据的聚合根
  public void updateEntity(VenueEntity entity, VenueAggregate aggregate) {
    if (entity == null || aggregate == null) {
      return;
    }

    // 更新来源信息
    if (aggregate.getProvenance() != null) {
      entity.setProvenanceCode(aggregate.getProvenance().code());
      entity.setSourceCreatedDate(aggregate.getProvenance().sourceCreatedDate());
      entity.setSourceUpdatedDate(aggregate.getProvenance().sourceUpdatedDate());
      entity.setLastSyncedAt(aggregate.getProvenance().lastSyncedAt());
    }

    // 更新嵌入式值对象
    entity.setPublicationProfile(aggregate.getPublicationProfile());
    entity.setCitationMetrics(aggregate.getCitationMetrics());
    entity.setOpenAccess(aggregate.getOpenAccess());
    entity.setAffiliatedSocieties(aggregate.getAffiliatedSocieties());

    // 更新快速访问字段
    populateQuickAccessFields(entity, aggregate);
  }

  // ========== 私有辅助方法 ==========

  /// 填充快速访问字段。
  ///
  /// @param entity JPA 实体
  /// @param aggregate 聚合根
  private void populateQuickAccessFields(VenueEntity entity, VenueAggregate aggregate) {
    // 从标识符提取快速访问字段
    aggregate.getIdentifier(VenueIdentifierType.NLM).ifPresent(entity::setNlmId);
    aggregate.getIdentifier(VenueIdentifierType.ISSN_L).ifPresent(entity::setIssnL);
    aggregate.getIdentifier(VenueIdentifierType.OPENALEX).ifPresent(entity::setOpenalexId);

    // 从 publicationProfile 提取快速访问字段
    if (aggregate.getPublicationProfile() != null) {
      entity.setAbbreviatedTitle(aggregate.getPublicationProfile().abbreviatedTitle());
      entity.setPrimaryLanguage(aggregate.getPublicationProfile().getMainLanguage());
      entity.setCountryCode(aggregate.getPublicationProfile().countryCode());
    }
  }

  // ========== ID 转换方法 ==========

  /// 将 VenueId 转换为 Long。
  @Named("venueIdToLong")
  Long venueIdToLong(VenueId id) {
    return id != null ? id.value() : null;
  }

  // ========== 枚举转换方法 ==========

  /// 将 VenueType 枚举转换为 String。
  @Named("venueTypeToString")
  String venueTypeToString(VenueType type) {
    return type != null ? type.getCode() : null;
  }

  /// 将 String 转换为 VenueType 枚举。
  VenueType stringToVenueType(String type) {
    return type != null ? VenueType.fromCode(type) : null;
  }

  /// 将 String 转换为 VenueIdentifierType 枚举。
  VenueIdentifierType stringToIdentifierType(String type) {
    return type != null ? VenueIdentifierType.fromCode(type) : null;
  }
}
