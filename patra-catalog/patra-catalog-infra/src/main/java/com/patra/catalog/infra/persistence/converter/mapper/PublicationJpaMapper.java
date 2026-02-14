package com.patra.catalog.infra.persistence.converter.mapper;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.enums.AbstractType;
import com.patra.catalog.domain.model.enums.DatePrecision;
import com.patra.catalog.domain.model.enums.IndexingStatus;
import com.patra.catalog.domain.model.enums.OaLocationType;
import com.patra.catalog.domain.model.enums.OaStatus;
import com.patra.catalog.domain.model.enums.PublicationDateType;
import com.patra.catalog.domain.model.enums.PublicationMedium;
import com.patra.catalog.domain.model.enums.PublicationStatus;
import com.patra.catalog.domain.model.enums.QualityLevel;
import com.patra.catalog.domain.model.enums.QualityScore;
import com.patra.catalog.domain.model.enums.ReviewStatus;
import com.patra.catalog.domain.model.enums.TranslationType;
import com.patra.catalog.domain.model.enums.VersionType;
import com.patra.catalog.domain.model.vo.publication.LanguageInfo;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationAlternativeAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationDate;
import com.patra.catalog.domain.model.vo.publication.PublicationId;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import com.patra.catalog.domain.model.vo.publication.PublicationOaLocation;
import com.patra.catalog.domain.model.vo.venue.VenueId;
import com.patra.catalog.domain.model.vo.venue.VenueInstanceId;
import com.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import com.patra.catalog.infra.persistence.entity.PublicationAlternativeAbstractEntity;
import com.patra.catalog.infra.persistence.entity.PublicationDateEntity;
import com.patra.catalog.infra.persistence.entity.PublicationEntity;
import com.patra.catalog.infra.persistence.entity.PublicationIdentifierEntity;
import com.patra.catalog.infra.persistence.entity.PublicationMetadataEntity;
import com.patra.catalog.infra.persistence.entity.PublicationOaLocationEntity;
import com.patra.common.enums.ProvenanceCode;
import java.util.List;
import java.util.Map;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 文献出版物 JPA 实体转换器。
///
/// **职责**：
///
/// - `PublicationAggregate` ↔ `PublicationEntity` 双向转换
/// - 嵌入式值对象（`LanguageInfo`）的展开与重建
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
public abstract class PublicationJpaMapper {

  /// 将聚合根转换为 JPA 实体。
  ///
  /// @param aggregate 文献聚合根
  /// @return JPA 实体
  @Mapping(target = "id", source = "id", qualifiedByName = "publicationIdToLong")
  @Mapping(
      target = "provenanceCode",
      source = "provenanceCode",
      qualifiedByName = "provenanceCodeToString")
  @Mapping(target = "pmid", source = "pmid")
  @Mapping(target = "doi", source = "doi")
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
  public abstract PublicationEntity toEntity(PublicationAggregate aggregate);

  /// 将 JPA 实体转换为聚合根。
  ///
  /// 使用 `PublicationAggregate.restore()` 工厂方法重建聚合根。
  ///
  /// **注意**：`extendedIdentifiers` 和 `publicationAbstract` 需要单独加载并设置。
  ///
  /// @param entity JPA 实体
  /// @return 文献聚合根
  public PublicationAggregate toAggregate(PublicationEntity entity) {
    if (entity == null) {
      return null;
    }

    // 重建嵌入式值对象
    LanguageInfo languageInfo = rebuildLanguageInfo(entity);

    return PublicationAggregate.restore(
        entity.getId() != null ? PublicationId.of(entity.getId()) : null,
        stringToProvenanceCode(entity.getProvenanceCode()),
        entity.getPmid(),
        entity.getDoi(),
        List.of(), // extendedIdentifiers 单独加载
        entity.getVenueId() != null ? VenueId.of(entity.getVenueId()) : null,
        VenueInstanceId.of(entity.getVenueInstanceId()),
        entity.getTitle(),
        entity.getOriginalTitle(),
        languageInfo,
        null, // publicationAbstract 单独加载
        stringToPublicationStatus(entity.getPublicationStatus()),
        stringToPublicationMedium(entity.getMediaType()),
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

  /// 将 PublicationMedium 枚举转换为 String。
  @Named("mediaTypeToString")
  String mediaTypeToString(PublicationMedium type) {
    return type != null ? type.name() : null;
  }

  /// 将 String 转换为 PublicationMedium 枚举。
  PublicationMedium stringToPublicationMedium(String type) {
    return type != null ? PublicationMedium.valueOf(type) : null;
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

  // ========== 标识符转换 ==========

  private static final Logger log = LoggerFactory.getLogger(PublicationJpaMapper.class);
  private static final ObjectMapper objectMapper = new ObjectMapper();

  /// 将标识符值对象转换为 JPA 实体。
  ///
  /// @param identifier 标识符值对象
  /// @param publicationId 文献 ID
  /// @return JPA 实体
  public PublicationIdentifierEntity toIdentifierEntity(
      PublicationIdentifier identifier, Long publicationId) {
    if (identifier == null) {
      return null;
    }
    PublicationIdentifierEntity entity = new PublicationIdentifierEntity();
    entity.setPublicationId(publicationId);
    entity.setType(identifier.type());
    entity.setValue(identifier.value());
    entity.setSource(identifier.source());
    return entity;
  }

  /// 将 JPA 实体转换为标识符值对象。
  ///
  /// @param entity JPA 实体
  /// @return 标识符值对象
  public PublicationIdentifier toIdentifier(PublicationIdentifierEntity entity) {
    if (entity == null) {
      return null;
    }
    return new PublicationIdentifier(entity.getType(), entity.getValue(), entity.getSource());
  }

  // ========== 摘要转换 ==========

  /// 将摘要值对象转换为 JPA 实体。
  ///
  /// @param pubAbstract 摘要值对象
  /// @param publicationId 文献 ID
  /// @return JPA 实体
  public PublicationAbstractEntity toAbstractEntity(
      PublicationAbstract pubAbstract, Long publicationId) {
    if (pubAbstract == null) {
      return null;
    }
    PublicationAbstractEntity entity = new PublicationAbstractEntity();
    entity.setPublicationId(publicationId);
    entity.setPlainText(pubAbstract.plainText());
    entity.setStructuredSections(mapToJson(pubAbstract.structuredSections()));
    entity.setCopyright(pubAbstract.copyright());
    entity.setAbstractType(
        pubAbstract.abstractType() != null ? pubAbstract.abstractType().name() : null);
    return entity;
  }

  /// 将 JPA 实体转换为摘要值对象。
  ///
  /// @param entity JPA 实体
  /// @return 摘要值对象
  public PublicationAbstract toAbstract(PublicationAbstractEntity entity) {
    if (entity == null) {
      return null;
    }
    return PublicationAbstract.builder()
        .plainText(entity.getPlainText())
        .structuredSections(jsonToMap(entity.getStructuredSections()))
        .copyright(entity.getCopyright())
        .abstractType(stringToAbstractType(entity.getAbstractType()))
        .build();
  }

  // ========== 日期转换 ==========

  /// 将日期值对象转换为 JPA 实体。
  ///
  /// @param date 日期值对象
  /// @param publicationId 文献 ID
  /// @return JPA 实体
  public PublicationDateEntity toDateEntity(PublicationDate date, Long publicationId) {
    if (date == null) {
      return null;
    }
    PublicationDateEntity entity = new PublicationDateEntity();
    entity.setPublicationId(publicationId);
    entity.setDateType(date.dateType() != null ? date.dateType().getCode() : null);
    entity.setDateValue(date.toLocalDateOrNull());
    entity.setYear(date.year());
    entity.setMonth(date.month());
    entity.setDay(date.day());
    entity.setDatePrecision(date.datePrecision() != null ? date.datePrecision().getCode() : null);
    entity.setSeason(date.season());
    entity.setDateString(date.dateString());
    entity.setIsPrimary(date.isPrimary());
    entity.setOrderNum(date.orderNum());
    return entity;
  }

  /// 将 JPA 实体转换为日期值对象。
  ///
  /// @param entity JPA 实体
  /// @return 日期值对象
  public PublicationDate toDate(PublicationDateEntity entity) {
    if (entity == null) {
      return null;
    }
    return PublicationDate.builder()
        .dateType(stringToDateType(entity.getDateType()))
        .year(entity.getYear())
        .month(entity.getMonth())
        .day(entity.getDay())
        .datePrecision(stringToDatePrecision(entity.getDatePrecision()))
        .season(entity.getSeason())
        .dateString(entity.getDateString())
        .isPrimary(Boolean.TRUE.equals(entity.getIsPrimary()))
        .orderNum(entity.getOrderNum())
        .build();
  }

  // ========== 元数据转换 ==========

  /// 将元数据值对象转换为 JPA 实体。
  ///
  /// @param metadata 元数据值对象
  /// @param publicationId 文献 ID
  /// @return JPA 实体
  public PublicationMetadataEntity toMetadataEntity(
      PublicationMetadata metadata, Long publicationId) {
    if (metadata == null) {
      return null;
    }
    PublicationMetadataEntity entity = new PublicationMetadataEntity();
    entity.setPublicationId(publicationId);
    entity.setIndexingStatus(
        metadata.indexingStatus() != null ? metadata.indexingStatus().getCode() : null);
    entity.setIndexingMethod(metadata.indexingMethod());
    entity.setIndexedDate(metadata.indexedDate());
    entity.setDataSource(metadata.dataSource() != null ? metadata.dataSource().getCode() : null);
    entity.setImportBatch(metadata.importBatch());
    entity.setImportDate(metadata.importDate());
    entity.setOwner(metadata.owner());
    entity.setCitationSubset(metadata.citationSubset());
    entity.setQualityScore(metadata.qualityScore() != null ? metadata.qualityScore().name() : null);
    entity.setCompletenessScore(
        metadata.completenessScore() != null ? metadata.completenessScore().name() : null);
    entity.setHasFullText(metadata.hasFullText());
    entity.setFullTextUrl(metadata.fullTextUrl());
    entity.setReviewStatus(metadata.reviewStatus() != null ? metadata.reviewStatus().name() : null);
    entity.setReviewDate(metadata.reviewDate());
    entity.setReviewer(metadata.reviewer());
    entity.setValidationErrors(listToJson(metadata.validationErrors()));
    entity.setProcessingNotes(listToJson(metadata.processingNotes()));
    return entity;
  }

  /// 将 JPA 实体转换为元数据值对象。
  ///
  /// @param entity JPA 实体
  /// @return 元数据值对象
  public PublicationMetadata toMetadata(PublicationMetadataEntity entity) {
    if (entity == null) {
      return null;
    }
    return PublicationMetadata.builder()
        .indexingStatus(stringToIndexingStatus(entity.getIndexingStatus()))
        .indexingMethod(entity.getIndexingMethod())
        .indexedDate(entity.getIndexedDate())
        .dataSource(stringToProvenanceCode(entity.getDataSource()))
        .importBatch(entity.getImportBatch())
        .importDate(entity.getImportDate())
        .owner(entity.getOwner())
        .citationSubset(entity.getCitationSubset())
        .qualityScore(stringToQualityScore(entity.getQualityScore()))
        .completenessScore(stringToQualityScore(entity.getCompletenessScore()))
        .hasFullText(Boolean.TRUE.equals(entity.getHasFullText()))
        .fullTextUrl(entity.getFullTextUrl())
        .reviewStatus(stringToReviewStatus(entity.getReviewStatus()))
        .reviewDate(entity.getReviewDate())
        .reviewer(entity.getReviewer())
        .validationErrors(jsonToList(entity.getValidationErrors()))
        .processingNotes(jsonToList(entity.getProcessingNotes()))
        .build();
  }

  // ========== 翻译摘要转换 ==========

  /// 将翻译摘要值对象转换为 JPA 实体。
  ///
  /// @param altAbstract 翻译摘要值对象
  /// @param publicationId 文献 ID
  /// @return JPA 实体
  public PublicationAlternativeAbstractEntity toAlternativeAbstractEntity(
      PublicationAlternativeAbstract altAbstract, Long publicationId) {
    if (altAbstract == null) {
      return null;
    }
    PublicationAlternativeAbstractEntity entity = new PublicationAlternativeAbstractEntity();
    entity.setPublicationId(publicationId);
    entity.setLanguageCode(altAbstract.languageCode());
    entity.setSourceType(altAbstract.sourceType());
    entity.setLanguageName(altAbstract.languageName());
    entity.setPlainText(altAbstract.plainText());
    entity.setStructuredSections(mapToJson(altAbstract.structuredSections()));
    entity.setTranslationType(
        altAbstract.translationType() != null ? altAbstract.translationType().name() : null);
    entity.setTranslator(altAbstract.translator());
    entity.setTranslationDate(altAbstract.translationDate());
    entity.setQualityLevel(
        altAbstract.qualityLevel() != null ? altAbstract.qualityLevel().name() : null);
    entity.setIsOfficial(altAbstract.isOfficial());
    entity.setOrderNum(altAbstract.orderNum());
    return entity;
  }

  /// 将 JPA 实体转换为翻译摘要值对象。
  ///
  /// @param entity JPA 实体
  /// @return 翻译摘要值对象
  public PublicationAlternativeAbstract toAlternativeAbstract(
      PublicationAlternativeAbstractEntity entity) {
    if (entity == null) {
      return null;
    }
    return PublicationAlternativeAbstract.builder()
        .languageCode(entity.getLanguageCode())
        .sourceType(entity.getSourceType())
        .languageName(entity.getLanguageName())
        .plainText(entity.getPlainText())
        .structuredSections(jsonToMap(entity.getStructuredSections()))
        .translationType(stringToTranslationType(entity.getTranslationType()))
        .translator(entity.getTranslator())
        .translationDate(entity.getTranslationDate())
        .qualityLevel(stringToQualityLevel(entity.getQualityLevel()))
        .isOfficial(Boolean.TRUE.equals(entity.getIsOfficial()))
        .orderNum(entity.getOrderNum())
        .build();
  }

  // ========== OA 位置转换 ==========

  /// 将 OA 位置值对象转换为 JPA 实体。
  ///
  /// @param location OA 位置值对象
  /// @param publicationId 文献 ID
  /// @return JPA 实体
  public PublicationOaLocationEntity toOaLocationEntity(
      PublicationOaLocation location, Long publicationId) {
    if (location == null) {
      return null;
    }
    PublicationOaLocationEntity entity = new PublicationOaLocationEntity();
    entity.setPublicationId(publicationId);
    entity.setOaStatus(location.oaStatus() != null ? location.oaStatus().name() : null);
    entity.setLocationType(
        location.locationType() != null ? location.locationType().getCode() : null);
    entity.setUrl(location.url());
    entity.setHostDomain(location.hostDomain());
    entity.setRepositoryName(location.repositoryName());
    entity.setRepositoryId(location.repositoryId());
    entity.setVersionType(location.versionType() != null ? location.versionType().getCode() : null);
    entity.setLicense(location.license());
    entity.setAvailableDate(location.availableDate());
    entity.setEmbargoEndDate(location.embargoEndDate());
    entity.setIsBest(location.isBest());
    entity.setPriority(location.priority());
    entity.setEvidenceSource(location.evidenceSource());
    entity.setCheckedDate(location.checkedDate());
    entity.setIsActive(location.isActive());
    entity.setPmcid(location.pmcid());
    return entity;
  }

  /// 将 JPA 实体转换为 OA 位置值对象。
  ///
  /// @param entity JPA 实体
  /// @return OA 位置值对象
  public PublicationOaLocation toOaLocation(PublicationOaLocationEntity entity) {
    if (entity == null) {
      return null;
    }
    return PublicationOaLocation.builder()
        .oaStatus(stringToOaStatus(entity.getOaStatus()))
        .locationType(stringToOaLocationType(entity.getLocationType()))
        .url(entity.getUrl())
        .hostDomain(entity.getHostDomain())
        .repositoryName(entity.getRepositoryName())
        .repositoryId(entity.getRepositoryId())
        .versionType(stringToVersionType(entity.getVersionType()))
        .license(entity.getLicense())
        .availableDate(entity.getAvailableDate())
        .embargoEndDate(entity.getEmbargoEndDate())
        .isBest(Boolean.TRUE.equals(entity.getIsBest()))
        .priority(entity.getPriority())
        .evidenceSource(entity.getEvidenceSource())
        .checkedDate(entity.getCheckedDate())
        .isActive(Boolean.TRUE.equals(entity.getIsActive()))
        .pmcid(entity.getPmcid())
        .build();
  }

  // ========== 新增枚举转换方法 ==========

  /// 将 String 转换为 AbstractType 枚举。
  AbstractType stringToAbstractType(String type) {
    return StrUtil.isNotBlank(type) ? AbstractType.valueOf(type) : null;
  }

  /// 将 String 转换为 PublicationDateType 枚举。
  PublicationDateType stringToDateType(String type) {
    return StrUtil.isNotBlank(type) ? PublicationDateType.fromCode(type) : null;
  }

  /// 将 String 转换为 DatePrecision 枚举。
  DatePrecision stringToDatePrecision(String precision) {
    return StrUtil.isNotBlank(precision) ? DatePrecision.fromCode(precision) : null;
  }

  /// 将 String 转换为 IndexingStatus 枚举。
  IndexingStatus stringToIndexingStatus(String status) {
    return StrUtil.isNotBlank(status) ? IndexingStatus.fromCode(status) : null;
  }

  /// 将 String 转换为 QualityScore 枚举。
  QualityScore stringToQualityScore(String score) {
    return StrUtil.isNotBlank(score) ? QualityScore.valueOf(score) : null;
  }

  /// 将 String 转换为 ReviewStatus 枚举。
  ReviewStatus stringToReviewStatus(String status) {
    return StrUtil.isNotBlank(status) ? ReviewStatus.valueOf(status) : null;
  }

  /// 将 String 转换为 TranslationType 枚举。
  TranslationType stringToTranslationType(String type) {
    return StrUtil.isNotBlank(type) ? TranslationType.valueOf(type) : null;
  }

  /// 将 String 转换为 QualityLevel 枚举。
  QualityLevel stringToQualityLevel(String level) {
    return StrUtil.isNotBlank(level) ? QualityLevel.valueOf(level) : null;
  }

  /// 将 String 转换为 OaLocationType 枚举。
  OaLocationType stringToOaLocationType(String type) {
    return StrUtil.isNotBlank(type) ? OaLocationType.fromCode(type) : null;
  }

  /// 将 String 转换为 VersionType 枚举。
  VersionType stringToVersionType(String type) {
    return StrUtil.isNotBlank(type) ? VersionType.fromCode(type) : null;
  }

  // ========== JSON 转换辅助方法 ==========

  /// 将 Map 转换为 JSON 字符串。
  private String mapToJson(Map<String, String> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(map);
    } catch (JsonProcessingException e) {
      log.warn("Map 转 JSON 失败: {}", e.getMessage());
      return null;
    }
  }

  /// 将 JSON 字符串转换为 Map。
  private Map<String, String> jsonToMap(String json) {
    if (StrUtil.isBlank(json)) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("JSON 转 Map 失败: {}", e.getMessage());
      return Map.of();
    }
  }

  /// 将 List 转换为 JSON 字符串。
  private String listToJson(List<String> list) {
    if (list == null || list.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(list);
    } catch (JsonProcessingException e) {
      log.warn("List 转 JSON 失败: {}", e.getMessage());
      return null;
    }
  }

  /// 将 JSON 字符串转换为 List。
  private List<String> jsonToList(String json) {
    if (StrUtil.isBlank(json)) {
      return List.of();
    }
    try {
      return objectMapper.readValue(json, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("JSON 转 List 失败: {}", e.getMessage());
      return List.of();
    }
  }
}
