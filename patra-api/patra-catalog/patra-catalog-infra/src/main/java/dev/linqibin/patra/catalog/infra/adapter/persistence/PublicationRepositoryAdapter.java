package dev.linqibin.patra.catalog.infra.adapter.persistence;

import static dev.linqibin.commons.util.StringUtils.trimToNull;

import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import dev.linqibin.patra.catalog.domain.model.aggregate.PublicationAggregate;
import dev.linqibin.patra.catalog.domain.model.vo.publication.ExistingPublicationKeys;
import dev.linqibin.patra.catalog.domain.model.vo.publication.MeshQualifier;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationAlternativeAbstract;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationCompleteData;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationDate;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationFunding;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationId;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationInvestigator;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationKeyword;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationMeshHeading;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationMetadata;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationOaLocation;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationPersonalNameSubject;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationSupplMesh;
import dev.linqibin.patra.catalog.domain.model.vo.publication.PublicationTypeInfo;
import dev.linqibin.patra.catalog.domain.port.repository.PublicationRepository;
import dev.linqibin.patra.catalog.infra.persistence.converter.mapper.PublicationJpaMapper;
import dev.linqibin.patra.catalog.infra.persistence.dao.InvestigatorDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.KeywordDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationAbstractDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationAlternativeAbstractDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationDateDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationFundingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationIdentifierDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationInvestigatorDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationKeywordDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationMeshHeadingDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationMeshQualifierDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationMetadataDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationOaLocationDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationPersonalNameSubjectDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationSupplMeshDao;
import dev.linqibin.patra.catalog.infra.persistence.dao.PublicationTypeDao;
import dev.linqibin.patra.catalog.infra.persistence.entity.InvestigatorEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.KeywordEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationAbstractEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationAlternativeAbstractEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationDateEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationFundingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationIdentifierEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationInvestigatorEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationKeywordEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationMeshHeadingEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationMeshQualifierEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationMetadataEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationOaLocationEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationPersonalNameSubjectEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationSupplMeshEntity;
import dev.linqibin.patra.catalog.infra.persistence.entity.PublicationTypeEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import dev.linqibin.commons.util.StringUtils;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 文献聚合根仓储实现（JPA 版本）。
///
/// **职责**：
///
/// - 实现 `PublicationRepository` 接口，提供文献的持久化操作
/// - 负责 Aggregate ↔ Entity 转换
/// - 协调 JPA Repository 操作
///
/// **数据访问**：
///
/// - 使用 Spring Data JPA 进行数据库操作
/// - 批量插入使用 `saveAll()` 优化性能
/// - 使用 `PublicationJpaMapper` 进行 Entity ↔ 聚合根转换
///
/// **JPA 更新模式**：
///
/// - 新增：创建 Entity，分配雪花 ID，调用 `save()`
/// - 更新：通过 `EntityManager.find()` 获取托管实体，原地更新可变字段
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class PublicationRepositoryAdapter implements PublicationRepository {

  /// 批量操作时每批次的大小，超过此值会 flush 并 clear 以防内存溢出。
  private static final int BATCH_FLUSH_SIZE = 500;

  /// ICU4J Collator（匹配 MySQL utf8mb4_0900_ai_ci：忽略重音与大小写）。
  private static final Collator UNICODE_CI_COLLATOR;

  static {
    UNICODE_CI_COLLATOR = Collator.getInstance(ULocale.ROOT);
    UNICODE_CI_COLLATOR.setStrength(Collator.PRIMARY);
    UNICODE_CI_COLLATOR.freeze();
  }

  private final PublicationDao jpaRepository;
  private final PublicationJpaMapper jpaConverter;
  private final EntityManager entityManager;

  // ========== 补充数据 DAO ==========
  private final PublicationIdentifierDao identifierDao;
  private final PublicationAbstractDao abstractDao;
  private final PublicationDateDao dateDao;
  private final PublicationMetadataDao metadataDao;
  private final PublicationAlternativeAbstractDao alternativeAbstractDao;
  private final PublicationOaLocationDao oaLocationDao;

  // ========== 关联数据 DAO（用于 insertAllWithAssociations） ==========
  private final PublicationMeshHeadingDao meshHeadingDao;
  private final PublicationMeshQualifierDao meshQualifierDao;
  private final KeywordDao keywordMasterDao;
  private final PublicationKeywordDao keywordDao;
  private final PublicationFundingDao fundingDao;
  private final PublicationTypeDao typeDao;
  private final PublicationSupplMeshDao supplMeshDao;
  private final InvestigatorDao investigatorDao;
  private final PublicationInvestigatorDao publicationInvestigatorDao;
  private final PublicationPersonalNameSubjectDao personalNameSubjectDao;

  @Override
  public Optional<PublicationAggregate> findById(Long id) {
    if (id == null) {
      return Optional.empty();
    }
    return jpaRepository.findById(id).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<PublicationAggregate> findByPmid(String pmid) {
    if (pmid == null || pmid.isBlank()) {
      return Optional.empty();
    }
    return jpaRepository.findByPmid(pmid).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<PublicationAggregate> findByDoi(String doi) {
    if (doi == null || doi.isBlank()) {
      return Optional.empty();
    }
    return jpaRepository.findByDoi(doi).map(jpaConverter::toAggregate);
  }

  @Override
  public Optional<PublicationAggregate> findByPmidOrDoi(String pmid, String doi) {
    if ((pmid == null || pmid.isBlank()) && (doi == null || doi.isBlank())) {
      return Optional.empty();
    }
    return jpaRepository.findByPmidOrDoi(pmid, doi).map(jpaConverter::toAggregate);
  }

  @Override
  public boolean existsByPmid(String pmid) {
    if (pmid == null || pmid.isBlank()) {
      return false;
    }
    return jpaRepository.existsByPmid(pmid);
  }

  @Override
  public boolean existsByDoi(String doi) {
    if (doi == null || doi.isBlank()) {
      return false;
    }
    return jpaRepository.existsByDoi(doi);
  }

  @Override
  public ExistingPublicationKeys findExistingKeys(Set<String> pmids, Set<String> dois) {
    Set<String> normalizedPmids =
        pmids == null
            ? Set.of()
            : pmids.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    Set<String> normalizedDois =
        dois == null
            ? Set.of()
            : dois.stream()
                .map(this::normalizeDoi)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

    if (normalizedPmids.isEmpty() && normalizedDois.isEmpty()) {
      return ExistingPublicationKeys.empty();
    }

    List<PublicationEntity> matchedEntities;
    if (!normalizedPmids.isEmpty() && !normalizedDois.isEmpty()) {
      matchedEntities = jpaRepository.findByPmidInOrDoiIn(normalizedPmids, normalizedDois);
    } else if (!normalizedPmids.isEmpty()) {
      matchedEntities = jpaRepository.findByPmidIn(normalizedPmids);
    } else {
      matchedEntities = jpaRepository.findByDoiIn(normalizedDois);
    }

    Set<String> existingPmids = new HashSet<>();
    Set<String> existingDois = new HashSet<>();
    for (PublicationEntity entity : matchedEntities) {
      if (!normalizedPmids.isEmpty()) {
        String pmid = trimToNull(entity.getPmid());
        if (pmid != null) {
          existingPmids.add(pmid);
        }
      }

      if (!normalizedDois.isEmpty()) {
        String doi = normalizeDoi(entity.getDoi());
        if (doi != null) {
          existingDois.add(doi);
        }
      }
    }

    return ExistingPublicationKeys.of(existingPmids, existingDois);
  }

  @Override
  public List<PublicationAggregate> findByVenueInstanceId(Long venueInstanceId) {
    if (venueInstanceId == null) {
      return List.of();
    }
    return jpaRepository.findByVenueInstanceId(venueInstanceId).stream()
        .map(jpaConverter::toAggregate)
        .toList();
  }

  @Override
  public List<PublicationAggregate> findByVenueId(Long venueId) {
    if (venueId == null) {
      return List.of();
    }
    return jpaRepository.findByVenueId(venueId).stream().map(jpaConverter::toAggregate).toList();
  }

  @Override
  public long countByVenueId(Long venueId) {
    if (venueId == null) {
      return 0;
    }
    return jpaRepository.countByVenueId(venueId);
  }

  @Override
  public void save(PublicationAggregate aggregate) {
    if (aggregate == null) {
      throw new IllegalArgumentException("聚合根不能为 null");
    }

    PublicationEntity saved;

    if (aggregate.isTransient()) {
      // 新增：创建实体并持久化
      PublicationEntity entity = jpaConverter.toEntity(aggregate);
      assignIdIfMissing(entity);
      saved = jpaRepository.save(entity);
      // 回填 ID
      aggregate.assignId(PublicationId.of(saved.getId()));
    } else {
      // 更新：查找托管实体并原地更新
      PublicationEntity managed =
          entityManager.find(PublicationEntity.class, aggregate.getId().value());
      if (managed == null) {
        throw new IllegalStateException("实体不存在：id=" + aggregate.getId());
      }
      jpaConverter.updateEntity(managed, aggregate);
      saved = managed; // 托管实体会在事务提交时自动 flush
    }

    log.debug(
        "保存文献：id={}, pmid={}, doi={}, year={}",
        saved.getId(),
        aggregate.getPmid(),
        aggregate.getDoi(),
        aggregate.getPublicationYear());
  }

  @Override
  public void insertAll(List<PublicationAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    List<PublicationEntity> entities =
        aggregates.stream().map(jpaConverter::toEntity).peek(this::assignIdIfMissing).toList();

    List<PublicationEntity> savedEntities = jpaRepository.saveAll(entities);
    entityManager.flush();

    // 回填 ID 和版本
    for (int i = 0; i < aggregates.size(); i++) {
      PublicationAggregate aggregate = aggregates.get(i);
      PublicationEntity saved = savedEntities.get(i);
      aggregate.assignId(PublicationId.of(saved.getId()));
      aggregate.assignVersion(saved.getVersion());
    }

    log.info("批量插入文献完成：{} 条", aggregates.size());
  }

  @Override
  public void insertAllWithAssociations(List<PublicationCompleteData> data) {
    if (data == null || data.isEmpty()) {
      return;
    }

    try {
      // 1. 提取并写入 PublicationAggregate（ID 会被回填）
      List<PublicationAggregate> publications =
          data.stream().map(PublicationCompleteData::publication).toList();
      log.debug("批量写入 {} 条 Publication", publications.size());
      insertAll(publications);

      // 2. 写入关联数据
      writeMetadataAssociations(data);
      writeMeshAssociations(data);
      writeKeywordAssociations(data);
      writeFundingAssociations(data);
      writePublicationTypeAssociations(data);
      writeSupplMeshAssociations(data);
      writeAlternativeAbstractAssociations(data);
      writeDateAssociations(data);
      writeIdentifierAssociations(data);
      writeAbstractAssociations(data);
      writeInvestigatorAssociations(data);
      writePersonalNameSubjectAssociations(data);

      log.info("批量写入 {} 条 Publication（含关联数据）完成", data.size());
    } catch (RuntimeException ex) {
      entityManager.clear();
      throw ex;
    }
  }

  // ========== insertAllWithAssociations 辅助方法 ==========

  /// 写入文献元数据关联数据。
  private void writeMetadataAssociations(List<PublicationCompleteData> data) {
    List<PublicationMetadataEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasMetadata()) {
        continue;
      }

      Long publicationId = item.getPublicationId();
      PublicationMetadataEntity entity =
          jpaConverter.toMetadataEntity(item.metadata(), publicationId);
      entity.setId(SnowflakeIdGenerator.getId());
      entities.add(entity);
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, metadataDao);
      log.debug("写入 {} 条元数据", entities.size());
    }
  }

  /// 写入 MeSH 关联数据。
  private void writeMeshAssociations(List<PublicationCompleteData> data) {
    List<PublicationMeshHeadingEntity> headingEntities = new ArrayList<>();
    List<PublicationMeshQualifierEntity> qualifierEntities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasMeshHeadings()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationMeshHeading heading : item.meshHeadings()) {
        // 创建 Heading 实体（预分配 ID 用于关联 Qualifier）
        Long headingId = SnowflakeIdGenerator.getId();
        PublicationMeshHeadingEntity headingEntity =
            PublicationMeshHeadingEntity.builder()
                .id(headingId)
                .publicationId(publicationId)
                .descriptorUi(heading.descriptorUi())
                .majorTopic(heading.majorTopic())
                .headingOrder(heading.headingOrder())
                .build();
        headingEntities.add(headingEntity);

        // 创建 Qualifier 实体
        if (heading.hasQualifiers()) {
          for (MeshQualifier qualifier : heading.qualifiers()) {
            PublicationMeshQualifierEntity qualifierEntity =
                PublicationMeshQualifierEntity.builder()
                    .id(SnowflakeIdGenerator.getId())
                    .publicationMeshHeadingId(headingId)
                    .qualifierUi(qualifier.qualifierUi())
                    .majorTopic(qualifier.majorTopic())
                    .qualifierOrder(qualifier.qualifierOrder())
                    .build();
            qualifierEntities.add(qualifierEntity);
          }
        }
      }
    }

    // 批量保存
    if (!headingEntities.isEmpty()) {
      batchSaveWithFlush(headingEntities, meshHeadingDao);
      log.debug("写入 {} 条 MeSH Heading 关联", headingEntities.size());
    }

    if (!qualifierEntities.isEmpty()) {
      batchSaveWithFlush(qualifierEntities, meshQualifierDao);
      log.debug("写入 {} 条 MeSH Qualifier 关联", qualifierEntities.size());
    }
  }

  /// 写入关键词关联数据（规范化设计）。
  ///
  /// **写入流程**：
  /// 1. 收集所有关键词，计算规范化词形
  /// 2. 批量查询已存在的关键词（通过 normalized_term）
  /// 3. 对于不存在的关键词，创建新的 KeywordEntity 并插入
  /// 4. 创建 PublicationKeywordEntity 关联记录
  private void writeKeywordAssociations(List<PublicationCompleteData> data) {
    // 收集所有关键词及其来源
    Map<String, PublicationKeyword> keywordMap = new LinkedHashMap<>();
    for (PublicationCompleteData item : data) {
      if (!item.hasKeywords()) {
        continue;
      }
      for (PublicationKeyword keyword : item.keywords()) {
        String normalizedTerm = KeywordEntity.normalize(keyword.term());
        // 使用与 MySQL utf8mb4_0900_ai_ci 一致的 collation key 去重
        keywordMap.putIfAbsent(toCollationKey(normalizedTerm), keyword);
      }
    }

    if (keywordMap.isEmpty()) {
      return;
    }

    // 批量查询已存在的关键词
    List<String> normalizedTerms =
        keywordMap.values().stream()
            .map(k -> KeywordEntity.normalize(k.term()))
            .distinct()
            .toList();

    List<KeywordEntity> existingKeywords = keywordMasterDao.findByNormalizedTermIn(normalizedTerms);

    // 构建 normalized_term -> KeywordEntity 的映射
    Map<String, KeywordEntity> existingKeywordMap =
        existingKeywords.stream()
            .collect(
                Collectors.toMap(
                    entity -> toCollationKey(entity.getNormalizedTerm()), e -> e, (a, b) -> a));

    // 创建不存在的关键词
    List<KeywordEntity> newKeywords = new ArrayList<>();
    for (PublicationKeyword keyword : keywordMap.values()) {
      String normalizedTerm = KeywordEntity.normalize(keyword.term());
      String dedupKey = toCollationKey(normalizedTerm);
      if (!existingKeywordMap.containsKey(dedupKey)) {
        KeywordEntity entity = KeywordEntity.of(keyword.term(), keyword.source(), null);
        entity.setId(SnowflakeIdGenerator.getId());
        newKeywords.add(entity);
        existingKeywordMap.put(dedupKey, entity);
      }
    }

    if (!newKeywords.isEmpty()) {
      batchSaveWithFlush(newKeywords, keywordMasterDao);
      log.debug("新增 {} 条关键词", newKeywords.size());
    }

    // 创建关联记录
    List<PublicationKeywordEntity> associations = new ArrayList<>();
    for (PublicationCompleteData item : data) {
      if (!item.hasKeywords()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationKeyword keyword : item.keywords()) {
        String normalizedTerm = KeywordEntity.normalize(keyword.term());
        KeywordEntity keywordEntity = existingKeywordMap.get(toCollationKey(normalizedTerm));

        if (keywordEntity == null) {
          log.warn("关键词未找到: {}", keyword.term());
          continue;
        }

        PublicationKeywordEntity association =
            PublicationKeywordEntity.of(
                publicationId,
                keywordEntity.getId(),
                keyword.majorTopic(),
                keyword.keywordOrder(),
                keyword.source());
        association.setId(SnowflakeIdGenerator.getId());
        associations.add(association);
      }
    }

    if (!associations.isEmpty()) {
      batchSaveWithFlush(associations, keywordDao);
      log.debug("写入 {} 条关键词关联", associations.size());
    }
  }

  /// 写入资助信息关联数据。
  private void writeFundingAssociations(List<PublicationCompleteData> data) {
    List<PublicationFundingEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasFunding()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationFunding funding : item.funding()) {
        entities.add(
            PublicationFundingEntity.builder()
                .id(SnowflakeIdGenerator.getId())
                .publicationId(publicationId)
                .organizationId(funding.organizationId())
                .grantId(funding.grantId())
                .funderNameRaw(funding.funderNameRaw())
                .funderAcronymRaw(funding.funderAcronymRaw())
                .funderIdentifierRaw(funding.funderIdentifierRaw())
                .countryRaw(funding.countryRaw())
                .fundingOrder(funding.fundingOrder() != null ? funding.fundingOrder() : 1)
                .provenanceCode(funding.provenanceCode())
                .build());
      }
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, fundingDao);
      log.debug("写入 {} 条资助信息关联", entities.size());
    }
  }

  /// 写入出版类型关联数据。
  private void writePublicationTypeAssociations(List<PublicationCompleteData> data) {
    List<PublicationTypeEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasPublicationTypes()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationTypeInfo type : item.publicationTypes()) {
        var entity =
            PublicationTypeEntity.of(
                publicationId,
                type.typeId(),
                type.typeValue(),
                type.vocabularySource(),
                type.typeOrder());
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, typeDao);
      log.debug("写入 {} 条出版类型关联", entities.size());
    }
  }

  /// 写入补充 MeSH 概念关联数据。
  private void writeSupplMeshAssociations(List<PublicationCompleteData> data) {
    List<PublicationSupplMeshEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasSupplMeshList()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationSupplMesh supplMesh : item.supplMeshList()) {
        entities.add(
            PublicationSupplMeshEntity.builder()
                .id(SnowflakeIdGenerator.getId())
                .publicationId(publicationId)
                .scrUi(supplMesh.scrUi())
                .supplOrder(supplMesh.supplOrder())
                .build());
      }
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, supplMeshDao);
      log.debug("写入 {} 条补充 MeSH 概念关联", entities.size());
    }
  }

  /// 写入翻译摘要关联数据。
  private void writeAlternativeAbstractAssociations(List<PublicationCompleteData> data) {
    List<PublicationAlternativeAbstractEntity> entities =
        collectAlternativeAbstractEntitiesFromCompleteData(data);

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, alternativeAbstractDao);
      log.debug("写入 {} 条翻译摘要关联", entities.size());
    }
  }

  /// 写入日期关联数据。
  private void writeDateAssociations(List<PublicationCompleteData> data) {
    List<PublicationDateEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasDates()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationDate dateData : item.dates()) {
        PublicationDateEntity entity = jpaConverter.toDateEntity(dateData, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());

        // 完整日期时填充 dateValue
        if (dateData.isComplete()) {
          entity.setDateValue(LocalDate.of(dateData.year(), dateData.month(), dateData.day()));
        }

        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, dateDao);
      log.debug("写入 {} 条日期关联", entities.size());
    }
  }

  /// 写入标识符关联数据。
  private void writeIdentifierAssociations(List<PublicationCompleteData> data) {
    List<PublicationIdentifierEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      PublicationAggregate publication = item.publication();
      List<PublicationIdentifier> identifiers = publication.getExtendedIdentifiers();
      if (identifiers.isEmpty()) {
        continue;
      }

      Long publicationId = item.getPublicationId();

      for (PublicationIdentifier identifier : identifiers) {
        PublicationIdentifierEntity entity =
            jpaConverter.toIdentifierEntity(identifier, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, identifierDao);
      log.debug("写入 {} 条标识符关联", entities.size());
    }
  }

  /// 写入摘要关联数据。
  private void writeAbstractAssociations(List<PublicationCompleteData> data) {
    List<PublicationAbstractEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      PublicationAggregate publication = item.publication();

      // 只有有摘要内容时才写入
      if (!publication.hasAbstract()) {
        continue;
      }

      Long publicationId = item.getPublicationId();
      PublicationAbstract abstractContent = publication.getPublicationAbstract();

      // 使用 mapper 转换（处理 JSON 序列化）
      PublicationAbstractEntity entity =
          jpaConverter.toAbstractEntity(abstractContent, publicationId);
      entity.setId(SnowflakeIdGenerator.getId());
      entities.add(entity);
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, abstractDao);
      log.debug("写入 {} 条摘要", entities.size());
    }
  }

  /// 写入研究者关联数据。
  ///
  /// **去重策略**：
  ///
  /// 1. 收集本批次所有 ORCID 和 dedupKey
  /// 2. 批量查询已存在的研究者（优先 ORCID 匹配，其次 dedupKey 匹配）
  /// 3. 不存在则创建新研究者记录
  /// 4. 创建文献-研究者关联记录
  private void writeInvestigatorAssociations(List<PublicationCompleteData> data) {
    // 1. 收集研究者数据
    InvestigatorCollectionResult collectedData = collectInvestigatorsFromData(data);
    if (collectedData.dedupKeyToData().isEmpty()) {
      return;
    }

    // 2. 查询已存在的研究者
    ExistingInvestigatorMapping existingMapping =
        queryExistingInvestigators(collectedData.dedupKeyToData());

    // 3. 创建新研究者并建立 dedupKey → ID 映射
    Map<String, Long> dedupKeyToInvestigatorId =
        createNewInvestigatorsIfNeeded(collectedData.dedupKeyToData(), existingMapping);

    // 4. 创建关联记录
    createInvestigatorAssociations(
        collectedData.publicationInvestigators(), dedupKeyToInvestigatorId);
  }

  /// 研究者收集结果。
  private record InvestigatorCollectionResult(
      Map<String, PublicationInvestigator> dedupKeyToData,
      Map<Long, List<PublicationInvestigator>> publicationInvestigators) {}

  /// 研究者查询结果。
  private record ExistingInvestigatorMapping(
      Map<String, Long> byOrcid, Map<String, Long> byDedupKey) {}

  /// 从完整数据中收集研究者数据。
  private InvestigatorCollectionResult collectInvestigatorsFromData(
      List<PublicationCompleteData> data) {
    Map<String, PublicationInvestigator> dedupKeyToData = new LinkedHashMap<>();
    Map<Long, List<PublicationInvestigator>> publicationInvestigators = new LinkedHashMap<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasInvestigators()) {
        continue;
      }

      Long pubId = item.getPublicationId();
      List<PublicationInvestigator> invList = new ArrayList<>();

      for (PublicationInvestigator inv : item.investigators()) {
        dedupKeyToData.putIfAbsent(inv.dedupKey(), inv);
        invList.add(inv);
      }

      if (!invList.isEmpty()) {
        publicationInvestigators.put(pubId, invList);
      }
    }

    return new InvestigatorCollectionResult(dedupKeyToData, publicationInvestigators);
  }

  /// 查询已存在的研究者（优先 ORCID，其次 dedupKey）。
  private ExistingInvestigatorMapping queryExistingInvestigators(
      Map<String, PublicationInvestigator> dedupKeyToData) {
    // 按 ORCID 查询
    Set<String> orcids =
        dedupKeyToData.values().stream()
            .filter(PublicationInvestigator::hasOrcid)
            .map(PublicationInvestigator::orcid)
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toSet());

    Map<String, Long> existingByOrcid = new HashMap<>();
    if (!orcids.isEmpty()) {
      investigatorDao
          .findByOrcidIn(orcids)
          .forEach(e -> existingByOrcid.put(e.getOrcid(), e.getId()));
    }

    // 按 dedupKey 查询（排除已通过 ORCID 匹配的）
    Set<String> dedupKeysToQuery =
        dedupKeyToData.entrySet().stream()
            .filter(
                entry -> {
                  String orcid = entry.getValue().orcid();
                  return orcid == null || orcid.isBlank() || !existingByOrcid.containsKey(orcid);
                })
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    Map<String, Long> existingByDedupKey = new HashMap<>();
    if (!dedupKeysToQuery.isEmpty()) {
      investigatorDao
          .findByDedupKeyIn(dedupKeysToQuery)
          .forEach(e -> existingByDedupKey.put(e.getDedupKey(), e.getId()));
    }

    return new ExistingInvestigatorMapping(existingByOrcid, existingByDedupKey);
  }

  /// 创建新研究者记录（如果不存在）并返回 dedupKey → ID 映射。
  private Map<String, Long> createNewInvestigatorsIfNeeded(
      Map<String, PublicationInvestigator> dedupKeyToData,
      ExistingInvestigatorMapping existingMapping) {
    List<InvestigatorEntity> newInvestigators = new ArrayList<>();
    Map<String, Long> dedupKeyToInvestigatorId = new HashMap<>();

    for (Map.Entry<String, PublicationInvestigator> entry : dedupKeyToData.entrySet()) {
      String dedupKey = entry.getKey();
      PublicationInvestigator inv = entry.getValue();

      // 优先 ORCID 匹配
      Long existingId = null;
      if (inv.hasOrcid()) {
        existingId = existingMapping.byOrcid().get(inv.orcid());
      }

      // 其次 dedupKey 匹配
      if (existingId == null) {
        existingId = existingMapping.byDedupKey().get(dedupKey);
      }

      if (existingId != null) {
        dedupKeyToInvestigatorId.put(dedupKey, existingId);
      } else {
        Long newId = SnowflakeIdGenerator.getId();
        dedupKeyToInvestigatorId.put(dedupKey, newId);
        newInvestigators.add(buildInvestigatorEntity(newId, inv));
      }
    }

    if (!newInvestigators.isEmpty()) {
      batchSaveWithFlush(newInvestigators, investigatorDao);
      log.debug("创建 {} 条新研究者记录", newInvestigators.size());
    }

    return dedupKeyToInvestigatorId;
  }

  /// 创建文献-研究者关联记录。
  private void createInvestigatorAssociations(
      Map<Long, List<PublicationInvestigator>> publicationInvestigators,
      Map<String, Long> dedupKeyToInvestigatorId) {
    List<PublicationInvestigatorEntity> associations = new ArrayList<>();

    for (Map.Entry<Long, List<PublicationInvestigator>> entry :
        publicationInvestigators.entrySet()) {
      Long pubId = entry.getKey();

      for (PublicationInvestigator inv : entry.getValue()) {
        Long investigatorId = dedupKeyToInvestigatorId.get(inv.dedupKey());
        if (investigatorId == null) {
          log.warn("研究者 ID 查找失败：dedupKey={}", inv.dedupKey());
          continue;
        }

        PublicationInvestigatorEntity association =
            PublicationInvestigatorEntity.builder()
                .id(SnowflakeIdGenerator.getId())
                .publicationId(pubId)
                .investigatorId(investigatorId)
                .orderNum(inv.orderNum())
                .build();
        associations.add(association);
      }
    }

    if (!associations.isEmpty()) {
      batchSaveWithFlush(associations, publicationInvestigatorDao);
      log.debug("写入 {} 条研究者关联", associations.size());
    }
  }

  /// 构建研究者实体。
  private InvestigatorEntity buildInvestigatorEntity(Long id, PublicationInvestigator inv) {
    return InvestigatorEntity.builder()
        .id(id)
        .lastName(inv.lastName())
        .foreName(inv.foreName())
        .initials(inv.initials())
        .suffix(inv.suffix())
        .orcid(inv.orcid())
        .affiliationName(inv.affiliationName())
        .dedupKey(inv.dedupKey())
        .build();
  }

  /// 写入人物主题关联数据。
  private void writePersonalNameSubjectAssociations(List<PublicationCompleteData> data) {
    List<PublicationPersonalNameSubjectEntity> entities = new ArrayList<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasPersonalNameSubjects()) {
        continue;
      }

      Long pubId = item.getPublicationId();

      for (PublicationPersonalNameSubject subject : item.personalNameSubjects()) {
        PublicationPersonalNameSubjectEntity entity =
            PublicationPersonalNameSubjectEntity.builder()
                .publicationId(pubId)
                .lastName(subject.lastName())
                .foreName(subject.foreName())
                .initials(subject.initials())
                .suffix(subject.suffix())
                .dates(subject.dates())
                .description(subject.description())
                .subjectType(subject.subjectType())
                .identifier(subject.identifier())
                .orderNum(subject.orderNum())
                .build();
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, personalNameSubjectDao);
      log.debug("写入 {} 条人物主题关联", entities.size());
    }
  }

  @Override
  public void updateBatch(List<PublicationAggregate> aggregates) {
    if (aggregates == null || aggregates.isEmpty()) {
      return;
    }

    for (PublicationAggregate aggregate : aggregates) {
      if (aggregate.getId() == null) {
        throw new IllegalArgumentException("批量更新时聚合根 ID 不能为 null");
      }
      PublicationEntity managed =
          entityManager.find(PublicationEntity.class, aggregate.getId().value());
      if (managed != null) {
        jpaConverter.updateEntity(managed, aggregate);
      }
    }

    log.info("批量更新文献完成：{} 条", aggregates.size());
  }

  @Override
  public boolean deleteById(Long id) {
    if (id == null) {
      return false;
    }
    if (jpaRepository.existsById(id)) {
      jpaRepository.deleteById(id);
      log.debug("删除文献：id={}", id);
      return true;
    }
    return false;
  }

  @Override
  public int deleteByVenueInstanceId(Long venueInstanceId) {
    if (venueInstanceId == null) {
      return 0;
    }
    int deleted = jpaRepository.deleteByVenueInstanceId(venueInstanceId);
    log.debug("根据 venueInstanceId={} 删除文献：{} 条", venueInstanceId, deleted);
    return deleted;
  }

  @Override
  public int deleteByVenueId(Long venueId) {
    if (venueId == null) {
      return 0;
    }
    int deleted = jpaRepository.deleteByVenueId(venueId);
    log.debug("根据 venueId={} 删除文献：{} 条", venueId, deleted);
    return deleted;
  }

  /// 为没有 ID 的实体分配雪花 ID。
  ///
  /// @param entity JPA 实体
  private void assignIdIfMissing(PublicationEntity entity) {
    if (entity.getId() == null) {
      entity.setId(SnowflakeIdGenerator.getId());
    }
  }

  // ========== 标识符管理（聚合边界内，独立表） ==========

  @Override
  public void replaceIdentifiersBatch(
      Map<Long, List<PublicationIdentifier>> identifiersByPublicationId) {
    if (identifiersByPublicationId == null || identifiersByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(identifiersByPublicationId.keySet());

    // 删除旧数据
    identifierDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationIdentifierEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationIdentifier>> entry :
        identifiersByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationIdentifier identifier : entry.getValue()) {
        PublicationIdentifierEntity entity =
            jpaConverter.toIdentifierEntity(identifier, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, identifierDao);
      log.debug("批量插入标识符 {} 条", entities.size());
    }
  }

  // ========== 摘要管理（聚合边界内，独立表，1:1） ==========

  @Override
  public void replaceAbstractsBatch(Map<Long, PublicationAbstract> abstractsByPublicationId) {
    if (abstractsByPublicationId == null || abstractsByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(abstractsByPublicationId.keySet());

    // 删除旧数据
    abstractDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationAbstractEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, PublicationAbstract> entry : abstractsByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      PublicationAbstract pubAbstract = entry.getValue();
      if (pubAbstract != null && pubAbstract.hasContent()) {
        PublicationAbstractEntity entity =
            jpaConverter.toAbstractEntity(pubAbstract, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, abstractDao);
      log.debug("批量插入摘要 {} 条", entities.size());
    }
  }

  // ========== 补充数据管理（聚合边界外） ==========

  @Override
  public void replaceDatesBatch(Map<Long, List<PublicationDate>> datesByPublicationId) {
    if (datesByPublicationId == null || datesByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(datesByPublicationId.keySet());

    // 删除旧数据
    dateDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationDateEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationDate>> entry : datesByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationDate date : entry.getValue()) {
        PublicationDateEntity entity = jpaConverter.toDateEntity(date, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, dateDao);
      log.debug("批量插入日期 {} 条", entities.size());
    }
  }

  @Override
  public void replaceMetadataBatch(Map<Long, PublicationMetadata> metadataByPublicationId) {
    if (metadataByPublicationId == null || metadataByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(metadataByPublicationId.keySet());

    // 删除旧数据
    metadataDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationMetadataEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, PublicationMetadata> entry : metadataByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      PublicationMetadata metadata = entry.getValue();
      if (metadata != null) {
        PublicationMetadataEntity entity = jpaConverter.toMetadataEntity(metadata, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, metadataDao);
      log.debug("批量插入元数据 {} 条", entities.size());
    }
  }

  @Override
  public void replaceAlternativeAbstractsBatch(
      Map<Long, List<PublicationAlternativeAbstract>> abstractsByPublicationId) {
    if (abstractsByPublicationId == null || abstractsByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(abstractsByPublicationId.keySet());

    // 删除旧数据
    alternativeAbstractDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationAlternativeAbstractEntity> entities =
        collectAlternativeAbstractEntitiesFromMap(abstractsByPublicationId);

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, alternativeAbstractDao);
      log.debug("批量插入翻译摘要 {} 条", entities.size());
    }
  }

  @Override
  public void replaceOaLocationsBatch(
      Map<Long, List<PublicationOaLocation>> locationsByPublicationId) {
    if (locationsByPublicationId == null || locationsByPublicationId.isEmpty()) {
      return;
    }

    List<Long> publicationIds = new ArrayList<>(locationsByPublicationId.keySet());

    // 删除旧数据
    oaLocationDao.deleteByPublicationIdIn(publicationIds);
    entityManager.flush();

    // 收集新数据
    List<PublicationOaLocationEntity> entities = new ArrayList<>();
    for (Map.Entry<Long, List<PublicationOaLocation>> entry : locationsByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationOaLocation location : entry.getValue()) {
        PublicationOaLocationEntity entity =
            jpaConverter.toOaLocationEntity(location, publicationId);
        entity.setId(SnowflakeIdGenerator.getId());
        entities.add(entity);
      }
    }

    // 批量插入（带 flush）
    if (!entities.isEmpty()) {
      batchSaveWithFlush(entities, oaLocationDao);
      log.debug("批量插入 OA 位置 {} 条", entities.size());
    }
  }

  /// 生成与 MySQL utf8mb4_0900_ai_ci 一致的去重键。
  ///
  /// - 忽略重音差异（如 cáncer = cancer）
  /// - 忽略大小写差异
  /// - 基于 Unicode Collation Algorithm（ICU4J）
  private String toCollationKey(String value) {
    if (value == null) {
      return "";
    }
    byte[] keyBytes = UNICODE_CI_COLLATOR.getCollationKey(value).toByteArray();
    StringBuilder hex = new StringBuilder(keyBytes.length * 2);
    for (byte b : keyBytes) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  /// 标准化 DOI（trim + lower）。
  private String normalizeDoi(String doi) {
    String trimmed = trimToNull(doi);
    if (trimmed == null) {
      return null;
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }

  /// 从完整文献数据中收集翻译摘要实体并按（publicationId, languageCode, sourceType）去重。
  private List<PublicationAlternativeAbstractEntity>
      collectAlternativeAbstractEntitiesFromCompleteData(List<PublicationCompleteData> data) {
    LinkedHashMap<String, PublicationAlternativeAbstractEntity> deduplicated =
        new LinkedHashMap<>();

    for (PublicationCompleteData item : data) {
      if (!item.hasAlternativeAbstracts()) {
        continue;
      }

      Long publicationId = item.getPublicationId();
      for (PublicationAlternativeAbstract altAbstract : item.alternativeAbstracts()) {
        PublicationAlternativeAbstractEntity entity =
            jpaConverter.toAlternativeAbstractEntity(altAbstract, publicationId);
        if (entity == null) {
          continue;
        }
        entity.setId(SnowflakeIdGenerator.getId());
        putAlternativeAbstractIfAbsent(deduplicated, entity);
      }
    }

    return new ArrayList<>(deduplicated.values());
  }

  /// 从 publicationId -> 翻译摘要列表中收集实体并按（publicationId, languageCode, sourceType）去重。
  private List<PublicationAlternativeAbstractEntity> collectAlternativeAbstractEntitiesFromMap(
      Map<Long, List<PublicationAlternativeAbstract>> abstractsByPublicationId) {
    LinkedHashMap<String, PublicationAlternativeAbstractEntity> deduplicated =
        new LinkedHashMap<>();

    for (Map.Entry<Long, List<PublicationAlternativeAbstract>> entry :
        abstractsByPublicationId.entrySet()) {
      Long publicationId = entry.getKey();
      for (PublicationAlternativeAbstract altAbstract : entry.getValue()) {
        PublicationAlternativeAbstractEntity entity =
            jpaConverter.toAlternativeAbstractEntity(altAbstract, publicationId);
        if (entity == null) {
          continue;
        }
        entity.setId(SnowflakeIdGenerator.getId());
        putAlternativeAbstractIfAbsent(deduplicated, entity);
      }
    }

    return new ArrayList<>(deduplicated.values());
  }

  /// 按复合键放入翻译摘要，遇到重复键时保留首条并记录告警。
  private void putAlternativeAbstractIfAbsent(
      LinkedHashMap<String, PublicationAlternativeAbstractEntity> deduplicated,
      PublicationAlternativeAbstractEntity entity) {
    String key =
        buildAlternativeAbstractDedupKey(
            entity.getPublicationId(), entity.getLanguageCode(), entity.getSourceType());
    if (deduplicated.putIfAbsent(key, entity) != null) {
      log.warn(
          "跳过重复翻译摘要：publicationId={}, languageCode={}, sourceType={}, reason=IN_MEMORY_DUPLICATE",
          entity.getPublicationId(),
          entity.getLanguageCode(),
          entity.getSourceType());
    }
  }

  /// 构建翻译摘要去重键（publicationId + languageCode + sourceType）。
  private String buildAlternativeAbstractDedupKey(
      Long publicationId, String languageCode, String sourceType) {
    return publicationId
        + "|"
        + normalizeAlternativeAbstractKeyPart(languageCode)
        + "|"
        + normalizeAlternativeAbstractKeyPart(sourceType);
  }

  /// 规范化翻译摘要去重键组件。
  private String normalizeAlternativeAbstractKeyPart(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }

  // ========== 批量操作辅助方法 ==========

  /// 批量保存实体，定期 flush 以防内存溢出。
  ///
  /// 使用 `saveAll()` 批量保存，配合 `rewriteBatchedStatements=true` 启用 JDBC 批量插入，
  /// 相比逐条 `save()` 性能提升 10 倍以上。
  ///
  /// @param entities 实体列表
  /// @param repository JPA Repository
  /// @param <T> 实体类型
  private <T> void batchSaveWithFlush(
      List<T> entities, org.springframework.data.jpa.repository.JpaRepository<T, Long> repository) {
    if (entities.isEmpty()) {
      return;
    }

    // 分批保存，每批 BATCH_FLUSH_SIZE 条
    for (int i = 0; i < entities.size(); i += BATCH_FLUSH_SIZE) {
      int end = Math.min(i + BATCH_FLUSH_SIZE, entities.size());
      List<T> batch = entities.subList(i, end);
      repository.saveAll(batch); // 批量保存
      entityManager.flush();
      entityManager.clear();
    }
  }
}
