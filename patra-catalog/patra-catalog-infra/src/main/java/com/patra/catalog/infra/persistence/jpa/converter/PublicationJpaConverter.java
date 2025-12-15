package com.patra.catalog.infra.persistence.jpa.converter;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.enums.MediaType;
import com.patra.catalog.domain.model.enums.OaStatus;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifiers;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.infra.persistence.jpa.entity.PublicationEntity;
import com.patra.common.enums.ProvenanceCode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/// 文献出版物 JPA 实体转换器。
///
/// **职责**：
///
/// - `PublicationAggregate` ↔ `PublicationEntity` 双向转换
/// - 嵌入式值对象（`PublicationIdentifiers`、`LanguageInfo`）的展开与重建
/// - 枚举类型（`ProvenanceCode`、`PublicationStatus` 等）与 String 的映射
///
/// **特殊处理**：
///
/// - `language_base` 是数据库生成列，toEntity 时忽略
/// - 可变字段（`isOa`、`oaStatus`、`citationCount` 等）由 `updateEntity()` 处理
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring")
public abstract class PublicationJpaConverter {

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 文献聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "publicationIdToLong")
  @Mapping(
      target = "provenanceCode",
      source = "provenanceCode",
      qualifiedByName = "provenanceCodeToString")
  @Mapping(target = "pmid", source = "identifiers.pmid")
  @Mapping(target = "doi", source = "identifiers.doi")
  @Mapping(target = "venueId", source = "venueId", qualifiedByName = "venueIdToLong")
  @Mapping(
      target = "venueInstanceId",
      source = "venueInstanceId",
      qualifiedByName = "venueInstanceIdToLong")
  @Mapping(target = "languageRaw", source = "languageInfo.raw")
  @Mapping(target = "languageCode", source = "languageInfo.code")
  @Mapping(target = "languageBase", ignore = true) // 数据库生成列
  @Mapping(
      target = "publicationStatus",
      source = "publicationStatus",
      qualifiedByName = "publicationStatusToString")
  @Mapping(target = "mediaType", source = "mediaType", qualifiedByName = "mediaTypeToString")
  @Mapping(target = "oaStatus", source = "oaStatus", qualifiedByName = "oaStatusToString")
  @Mapping(target = "extData", ignore = true) // 暂未使用
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
  public abstract PublicationEntity toEntity(PublicationAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `PublicationAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// @param entity JPA 实体
  /// @return 文献聚合根
  public PublicationAggregate toAggregate(PublicationEntity entity) {
    if (entity == null) {
      return null;
    }

    // 重建嵌入式值对象
    PublicationIdentifiers identifiers = rebuildIdentifiers(entity);
    LanguageInfo languageInfo = rebuildLanguageInfo(entity);

    return PublicationAggregate.restore(
        entity.getId() != null ? PublicationId.of(entity.getId()) : null,
        stringToProvenanceCode(entity.getProvenanceCode()),
        identifiers,
        entity.getVenueId() != null ? VenueId.of(entity.getVenueId()) : null,
        VenueInstanceId.of(entity.getVenueInstanceId()),
        entity.getTitle(),
        entity.getOriginalTitle(),
        languageInfo,
        stringToPublicationStatus(entity.getPublicationStatus()),
        stringToMediaType(entity.getMediaType()),
        entity.getPublicationYear(),
        entity.getIsOa(),
        stringToOaStatus(entity.getOaStatus()),
        entity.getAuthorsComplete(),
        entity.getCitationCount(),
        entity.getNumberOfReferences(),
        entity.getConflictOfInterest(),
        entity.getLastSyncedAt(),
        entity.getVersion());
  }

  /// 更新托管实体的可变字段。
  ///
  /// Publication 的可变字段包括：isOa, oaStatus, citationCount, numberOfReferences, lastSyncedAt。
  ///
  /// @param entity 托管实体
  /// @param aggregate 包含更新数据的聚合根
  public void updateEntity(PublicationEntity entity, PublicationAggregate aggregate) {
    if (entity == null || aggregate == null) {
      return;
    }
    // 更新可变字段
    entity.setIsOa(aggregate.getIsOa());
    entity.setOaStatus(oaStatusToString(aggregate.getOaStatus()));
    entity.setCitationCount(aggregate.getCitationCount());
    entity.setNumberOfReferences(aggregate.getNumberOfReferences());
    entity.setLastSyncedAt(aggregate.getLastSyncedAt());
  }

  // ========== 值对象重建方法 ==========

  /// 重建 PublicationIdentifiers 值对象。
  private PublicationIdentifiers rebuildIdentifiers(PublicationEntity entity) {
    String pmid = entity.getPmid();
    String doi = entity.getDoi();
    if (pmid == null && doi == null) {
      return null;
    }
    return PublicationIdentifiers.of(pmid, doi);
  }

  /// 重建 LanguageInfo 值对象。
  private LanguageInfo rebuildLanguageInfo(PublicationEntity entity) {
    String code = entity.getLanguageCode();
    if (code == null) {
      return null;
    }
    // 使用 base 字段（数据库生成列），如果为空则自动推导
    String base = entity.getLanguageBase();
    if (base == null) {
      return LanguageInfo.of(entity.getLanguageRaw(), code);
    }
    return LanguageInfo.of(entity.getLanguageRaw(), code, base);
  }

  // ========== ID 转换方法 ==========

  /// 将 PublicationId 转换为 Long。
  @Named("publicationIdToLong")
  Long publicationIdToLong(PublicationId id) {
    return id != null ? id.value() : null;
  }

  /// 将 VenueId 转换为 Long。
  @Named("venueIdToLong")
  Long venueIdToLong(VenueId id) {
    return id != null ? id.value() : null;
  }

  /// 将 VenueInstanceId 转换为 Long。
  @Named("venueInstanceIdToLong")
  Long venueInstanceIdToLong(VenueInstanceId id) {
    return id != null ? id.value() : null;
  }

  // ========== 枚举转换方法 ==========

  /// 将 ProvenanceCode 枚举转换为 String。
  @Named("provenanceCodeToString")
  String provenanceCodeToString(ProvenanceCode code) {
    return code != null ? code.name() : null;
  }

  /// 将 String 转换为 ProvenanceCode 枚举。
  ProvenanceCode stringToProvenanceCode(String code) {
    return code != null ? ProvenanceCode.valueOf(code) : null;
  }

  /// 将 PublicationStatus 枚举转换为 String。
  @Named("publicationStatusToString")
  String publicationStatusToString(PublicationStatus status) {
    return status != null ? status.name() : null;
  }

  /// 将 String 转换为 PublicationStatus 枚举。
  PublicationStatus stringToPublicationStatus(String status) {
    return status != null ? PublicationStatus.valueOf(status) : null;
  }

  /// 将 MediaType 枚举转换为 String。
  @Named("mediaTypeToString")
  String mediaTypeToString(MediaType type) {
    return type != null ? type.name() : null;
  }

  /// 将 String 转换为 MediaType 枚举。
  MediaType stringToMediaType(String type) {
    return type != null ? MediaType.valueOf(type) : null;
  }

  /// 将 OaStatus 枚举转换为 String。
  @Named("oaStatusToString")
  String oaStatusToString(OaStatus status) {
    return status != null ? status.name() : null;
  }

  /// 将 String 转换为 OaStatus 枚举。
  OaStatus stringToOaStatus(String status) {
    return status != null ? OaStatus.valueOf(status) : null;
  }
}
