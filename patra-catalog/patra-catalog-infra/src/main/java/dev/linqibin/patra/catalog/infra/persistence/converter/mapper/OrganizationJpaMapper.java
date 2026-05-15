package dev.linqibin.patra.catalog.infra.persistence.converter.mapper;

import dev.linqibin.patra.catalog.domain.model.aggregate.OrganizationAggregate;
import dev.linqibin.patra.catalog.domain.model.enums.ExternalIdType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationNameType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationRelationType;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationStatus;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationType;
import dev.linqibin.patra.catalog.domain.model.vo.organization.ExternalId;
import dev.linqibin.patra.catalog.domain.model.vo.organization.GeoLocation;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationId;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationLink;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationName;
import dev.linqibin.patra.catalog.domain.model.vo.organization.OrganizationRelation;
import dev.linqibin.patra.catalog.domain.model.vo.organization.RorId;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationExternalIdEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationLocationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationNameEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.OrganizationRelationEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/// 机构 JPA 实体转换器。
///
/// **职责**：
///
/// - `OrganizationAggregate` ↔ `OrganizationEntity` 双向转换
/// - 子实体（Name、ExternalId、Relation、Location）的双向转换
/// - 枚举类型 ↔ String 的映射
///
/// **特殊处理**：
///
/// - 子集合需通过 `addXxxToAggregate` 方法添加到聚合根
/// - JSON 字段（types、domains、links）直接存储枚举代码
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class OrganizationJpaMapper {

  // ========== 主表转换 ==========

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 机构聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "orgIdToLong")
  @Mapping(target = "rorId", source = "rorId", qualifiedByName = "rorIdToString")
  @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
  @Mapping(target = "types", source = "aggregate", qualifiedByName = "typesToStrings")
  @Mapping(target = "links", source = "aggregate", qualifiedByName = "linksToEntities")
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
  @Mapping(target = "dedupKey", ignore = true)
  @Mapping(target = "metadata", ignore = true)
  public abstract OrganizationEntity toEntity(OrganizationAggregate aggregate);

  /// 更新已存在的 JPA 实体。
  ///
  /// 用于批量更新场景，保留实体的 ID、version 和审计字段，只更新业务字段。
  ///
  /// @param entity 已存在的 JPA 实体
  /// @param aggregate 机构聚合根
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "rorId", source = "rorId", qualifiedByName = "rorIdToString")
  @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
  @Mapping(target = "types", source = "aggregate", qualifiedByName = "typesToStrings")
  @Mapping(target = "links", source = "aggregate", qualifiedByName = "linksToEntities")
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
  @Mapping(target = "dedupKey", ignore = true)
  @Mapping(target = "metadata", ignore = true)
  public abstract void updateEntity(
      @MappingTarget OrganizationEntity entity, OrganizationAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `OrganizationAggregate.restore()` 工厂方法重建聚合根。
  /// **注意**：转换后需手动设置子集合和可选字段。
  ///
  /// @param entity JPA 实体
  /// @return 机构聚合根
  public OrganizationAggregate toAggregate(OrganizationEntity entity) {
    if (entity == null) {
      return null;
    }

    // 使用 restore 工厂方法重建聚合根
    OrganizationAggregate aggregate =
        OrganizationAggregate.restore(
            entity.getId() != null ? OrganizationId.of(entity.getId()) : null,
            RorId.of(entity.getRorId()),
            entity.getDisplayName(),
            stringToStatus(entity.getStatus()),
            entity.getVersion());

    // 设置可选字段
    if (entity.getEstablished() != null) {
      aggregate.withEstablished(entity.getEstablished());
    }
    if (entity.getAdminInfo() != null) {
      aggregate.withAdminInfo(entity.getAdminInfo());
    }

    // 设置 JSON 集合字段
    if (entity.getTypes() != null && !entity.getTypes().isEmpty()) {
      Set<OrganizationType> types =
          entity.getTypes().stream().map(OrganizationType::fromCode).collect(Collectors.toSet());
      aggregate.withTypes(types);
    }
    if (entity.getDomains() != null && !entity.getDomains().isEmpty()) {
      aggregate.withDomains(entity.getDomains());
    }
    if (entity.getLinks() != null && !entity.getLinks().isEmpty()) {
      aggregate.withLinks(entity.getLinks());
    }

    return aggregate;
  }

  // ========== Name 转换 ==========

  /// 将名称值对象转换为 JPA 实体。
  ///
  /// @param name 名称值对象
  /// @param orgId 机构 ID
  /// @return JPA 实体
  public OrganizationNameEntity toNameEntity(OrganizationName name, Long orgId) {
    if (name == null) {
      return null;
    }
    OrganizationNameEntity entity = new OrganizationNameEntity();
    entity.setId(name.id());
    entity.setOrgId(orgId);
    entity.setValue(name.value());
    entity.setLang(name.lang());
    if (name.types() != null && !name.types().isEmpty()) {
      entity.setTypes(
          name.types().stream().map(OrganizationNameType::getCode).collect(Collectors.toSet()));
    }
    return entity;
  }

  /// 将 JPA 实体转换为名称值对象。
  ///
  /// @param entity JPA 实体
  /// @return 名称值对象
  public OrganizationName toName(OrganizationNameEntity entity) {
    if (entity == null) {
      return null;
    }
    Set<OrganizationNameType> types = null;
    if (entity.getTypes() != null && !entity.getTypes().isEmpty()) {
      types =
          entity.getTypes().stream()
              .map(OrganizationNameType::fromCode)
              .collect(Collectors.toSet());
    }
    return OrganizationName.createWithId(
        entity.getId(), entity.getValue(), types, entity.getLang());
  }

  /// 为聚合根添加名称。
  ///
  /// @param aggregate 聚合根
  /// @param entities 名称实体列表
  public void addNamesToAggregate(
      OrganizationAggregate aggregate, List<OrganizationNameEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    List<OrganizationName> names = entities.stream().map(this::toName).toList();
    aggregate.withNames(names);
  }

  // ========== ExternalId 转换 ==========

  /// 将外部标识符值对象转换为 JPA 实体。
  ///
  /// @param externalId 外部标识符值对象
  /// @param orgId 机构 ID
  /// @return JPA 实体
  public OrganizationExternalIdEntity toExternalIdEntity(ExternalId externalId, Long orgId) {
    if (externalId == null) {
      return null;
    }
    OrganizationExternalIdEntity entity = new OrganizationExternalIdEntity();
    entity.setId(externalId.id());
    entity.setOrgId(orgId);
    entity.setIdType(externalId.type().getCode());
    entity.setAllValues(new ArrayList<>(externalId.allValues()));
    entity.setPreferredValue(externalId.preferred());
    return entity;
  }

  /// 将 JPA 实体转换为外部标识符值对象。
  ///
  /// @param entity JPA 实体
  /// @return 外部标识符值对象
  public ExternalId toExternalId(OrganizationExternalIdEntity entity) {
    if (entity == null) {
      return null;
    }
    return ExternalId.createWithId(
        entity.getId(),
        ExternalIdType.fromCode(entity.getIdType()),
        entity.getAllValues(),
        entity.getPreferredValue());
  }

  /// 为聚合根添加外部标识符。
  ///
  /// @param aggregate 聚合根
  /// @param entities 外部标识符实体列表
  public void addExternalIdsToAggregate(
      OrganizationAggregate aggregate, List<OrganizationExternalIdEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    List<ExternalId> externalIds = entities.stream().map(this::toExternalId).toList();
    aggregate.withExternalIds(externalIds);
  }

  // ========== Relation 转换 ==========

  /// 将关系值对象转换为 JPA 实体。
  ///
  /// @param relation 关系值对象
  /// @param orgId 机构 ID
  /// @return JPA 实体
  public OrganizationRelationEntity toRelationEntity(OrganizationRelation relation, Long orgId) {
    if (relation == null) {
      return null;
    }
    OrganizationRelationEntity entity = new OrganizationRelationEntity();
    entity.setId(relation.id());
    entity.setOrgId(orgId);
    entity.setRelationType(relation.type().getCode());
    entity.setRelatedRorId(relation.relatedRorId().getId());
    entity.setRelatedLabel(relation.relatedLabel());
    entity.setRelatedOrgId(relation.relatedOrgId());
    return entity;
  }

  /// 将 JPA 实体转换为关系值对象。
  ///
  /// @param entity JPA 实体
  /// @return 关系值对象
  public OrganizationRelation toRelation(OrganizationRelationEntity entity) {
    if (entity == null) {
      return null;
    }
    return OrganizationRelation.createWithId(
        entity.getId(),
        OrganizationRelationType.fromCode(entity.getRelationType()),
        RorId.fromId(entity.getRelatedRorId()),
        entity.getRelatedLabel(),
        entity.getRelatedOrgId());
  }

  /// 为聚合根添加关系。
  ///
  /// @param aggregate 聚合根
  /// @param entities 关系实体列表
  public void addRelationsToAggregate(
      OrganizationAggregate aggregate, List<OrganizationRelationEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    List<OrganizationRelation> relations = entities.stream().map(this::toRelation).toList();
    aggregate.withRelations(relations);
  }

  // ========== Location 转换 ==========

  /// 将地理位置值对象转换为 JPA 实体。
  ///
  /// @param location 地理位置值对象
  /// @param orgId 机构 ID
  /// @return JPA 实体
  public OrganizationLocationEntity toLocationEntity(GeoLocation location, Long orgId) {
    if (location == null) {
      return null;
    }
    OrganizationLocationEntity entity = new OrganizationLocationEntity();
    entity.setId(location.id());
    entity.setOrgId(orgId);
    entity.setGeonamesId(location.geonamesId());
    entity.setContinentCode(location.continentCode());
    entity.setContinentName(location.continentName());
    entity.setCountryCode(location.countryCode());
    entity.setCountryName(location.countryName());
    entity.setSubdivisionCode(location.subdivisionCode());
    entity.setSubdivisionName(location.subdivisionName());
    entity.setCityName(location.cityName());
    entity.setLatitude(location.latitude());
    entity.setLongitude(location.longitude());
    return entity;
  }

  /// 将 JPA 实体转换为地理位置值对象。
  ///
  /// @param entity JPA 实体
  /// @return 地理位置值对象
  public GeoLocation toLocation(OrganizationLocationEntity entity) {
    if (entity == null) {
      return null;
    }
    return GeoLocation.builder()
        .id(entity.getId())
        .geonamesId(entity.getGeonamesId())
        .continentCode(entity.getContinentCode())
        .continentName(entity.getContinentName())
        .countryCode(entity.getCountryCode())
        .countryName(entity.getCountryName())
        .subdivisionCode(entity.getSubdivisionCode())
        .subdivisionName(entity.getSubdivisionName())
        .cityName(entity.getCityName())
        .latitude(entity.getLatitude())
        .longitude(entity.getLongitude())
        .build();
  }

  /// 为聚合根添加地理位置。
  ///
  /// @param aggregate 聚合根
  /// @param entities 地理位置实体列表
  public void addLocationsToAggregate(
      OrganizationAggregate aggregate, List<OrganizationLocationEntity> entities) {
    if (aggregate == null || entities == null) {
      return;
    }
    List<GeoLocation> locations = entities.stream().map(this::toLocation).toList();
    aggregate.withLocations(locations);
  }

  // ========== ID 转换方法 ==========

  /// 将 OrganizationId 转换为 Long。
  @Named("orgIdToLong")
  Long orgIdToLong(OrganizationId id) {
    return id != null ? id.value() : null;
  }

  /// 将 RorId 转换为纯 ID 字符串（不含 URL 前缀）。
  @Named("rorIdToString")
  String rorIdToString(RorId rorId) {
    return rorId != null ? rorId.getId() : null;
  }

  // ========== 枚举转换方法 ==========

  /// 将 OrganizationStatus 枚举转换为 String。
  @Named("statusToString")
  String statusToString(OrganizationStatus status) {
    return status != null ? status.getCode() : null;
  }

  /// 将 String 转换为 OrganizationStatus 枚举。
  OrganizationStatus stringToStatus(String code) {
    return code != null ? OrganizationStatus.fromCode(code) : null;
  }

  /// 将机构类型集合转换为字符串集合（用于 JSON 存储）。
  @Named("typesToStrings")
  Set<String> typesToStrings(OrganizationAggregate aggregate) {
    if (aggregate == null || aggregate.getTypes() == null) {
      return null;
    }
    return aggregate.getTypes().stream().map(OrganizationType::getCode).collect(Collectors.toSet());
  }

  /// 将 OrganizationLink 列表转换为 JSON 存储格式。
  @Named("linksToEntities")
  List<OrganizationLink> linksToEntities(OrganizationAggregate aggregate) {
    if (aggregate == null || aggregate.getLinks() == null) {
      return null;
    }
    return new ArrayList<>(aggregate.getLinks());
  }
}
