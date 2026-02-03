package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.model.vo.publication.PublicationAbstract;
import com.patra.catalog.domain.model.vo.publication.PublicationIdentifier;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.AlternativeAbstractData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.InvestigatorData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PersonalNameSubjectData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationDateData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.SupplMeshData;
import com.patra.catalog.infra.adapter.persistence.converter.mapper.PublicationJpaMapper;
import com.patra.catalog.infra.adapter.persistence.dao.InvestigatorDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationAbstractDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationAlternativeAbstractDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDateDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationFundingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationIdentifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationInvestigatorDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationKeywordDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshHeadingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshQualifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMetadataDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationPersonalNameSubjectDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationSupplMeshDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationTypeDao;
import com.patra.catalog.infra.adapter.persistence.entity.InvestigatorEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationAbstractEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationAlternativeAbstractEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationDateEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationFundingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationIdentifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationInvestigatorEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationKeywordEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshHeadingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshQualifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMetadataEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationPersonalNameSubjectEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationSupplMeshEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationTypeEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// Publication 批量写入器。
///
/// **职责**：
///
/// - 将 Processor 处理后的 PublicationImportResult 写入数据库
/// - 写入主数据（PublicationAggregate）和关联数据
///
/// **写入流程**：
///
/// 1. 批量写入 PublicationAggregate（ID 会被回填）
/// 2. 使用回填的 ID 构建关联实体
/// 3. 批量写入关联数据：MeSH、Keywords、Funding、PublicationType
///
/// **性能优化**：
///
/// - 批量插入减少数据库往返次数
/// - chunk size 由 Job 配置决定（推荐 500）
///
/// **错误处理**：
///
/// 由 Spring Batch FaultTolerant 机制处理：
/// - 单条失败时跳过该记录
/// - 批量失败时回退到逐条处理
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class PublicationItemWriter implements ItemWriter<PublicationImportResult> {

  private final PublicationRepository publicationRepository;
  private final PublicationMeshHeadingDao meshHeadingDao;
  private final PublicationMeshQualifierDao meshQualifierDao;
  private final PublicationKeywordDao keywordDao;
  private final PublicationFundingDao fundingDao;
  private final PublicationTypeDao typeDao;
  private final PublicationSupplMeshDao supplMeshDao;
  private final PublicationAlternativeAbstractDao alternativeAbstractDao;
  private final PublicationDateDao dateDao;
  private final PublicationIdentifierDao identifierDao;
  private final PublicationAbstractDao abstractDao;
  private final PublicationMetadataDao metadataDao;
  private final InvestigatorDao investigatorDao;
  private final PublicationInvestigatorDao publicationInvestigatorDao;
  private final PublicationPersonalNameSubjectDao personalNameSubjectDao;
  private final PublicationJpaMapper jpaMapper;

  @Override
  public void write(Chunk<? extends PublicationImportResult> chunk) throws Exception {
    if (chunk.isEmpty()) {
      return;
    }

    List<PublicationImportResult> results = new ArrayList<>(chunk.getItems());

    // 1. 提取并写入 PublicationAggregate（ID 会被回填）
    List<PublicationAggregate> publications =
        results.stream().map(PublicationImportResult::publication).toList();
    log.debug("批量写入 {} 条 Publication", publications.size());
    publicationRepository.insertAll(publications);

    // 2. 写入关联数据
    writeMetadataAssociations(results);
    writeMeshAssociations(results);
    writeKeywordAssociations(results);
    writeFundingAssociations(results);
    writePublicationTypeAssociations(results);
    writeSupplMeshAssociations(results);
    writeAlternativeAbstractAssociations(results);
    writeDateAssociations(results);
    writeIdentifierAssociations(results);
    writeAbstractAssociations(results);
    writeInvestigatorAssociations(results);
    writePersonalNameSubjectAssociations(results);
  }

  // ========== Metadata 关联写入 ==========

  /// 写入文献元数据关联数据。
  ///
  /// 将索引状态、数据来源、导入批次等元数据写入 `cat_publication_metadata` 表。
  /// 文献与元数据是 1:1 关系，一篇文献最多有一条元数据记录。
  ///
  /// **写入字段**：
  ///
  /// - `publication_id` 文献 ID
  /// - `indexing_status` 索引状态（MEDLINE/PubMed-not-MEDLINE 等）
  /// - `indexing_method` 索引方法（Automated/Curated）
  /// - `data_source` 数据来源（PUBMED）
  /// - `import_batch` 导入批次标识
  /// - `import_date` 导入时间
  /// - `owner` 数据记录所有者（NLM/NASA/PIP 等）
  /// - `citation_subset` 引用子集标识（IM/AIM 等）
  private void writeMetadataAssociations(List<PublicationImportResult> results) {
    List<PublicationMetadataEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasMetadata()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();
      PublicationMetadataEntity entity =
          jpaMapper.toMetadataEntity(result.metadata(), publicationId);
      entity.setId(SnowflakeIdGenerator.getId());
      entities.add(entity);
    }

    if (!entities.isEmpty()) {
      metadataDao.saveAll(entities);
      log.debug("写入 {} 条元数据", entities.size());
    }
  }

  // ========== MeSH 关联写入 ==========

  /// 写入 MeSH 关联数据。
  ///
  /// **处理流程**：
  ///
  /// 1. 遍历每个 PublicationImportResult
  /// 2. 获取回填的 Publication ID
  /// 3. 为每个 MeshHeadingData 创建 Heading 实体并保存
  /// 4. 使用 Heading ID 创建 Qualifier 实体并批量保存
  private void writeMeshAssociations(List<PublicationImportResult> results) {
    List<PublicationMeshHeadingEntity> headingEntities = new ArrayList<>();
    List<PublicationMeshQualifierEntity> qualifierEntities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasMeshHeadings()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (MeshHeadingData heading : result.meshHeadings()) {
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
          for (QualifierData qualifier : heading.qualifiers()) {
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
      meshHeadingDao.saveAll(headingEntities);
      log.debug("写入 {} 条 MeSH Heading 关联", headingEntities.size());
    }

    if (!qualifierEntities.isEmpty()) {
      meshQualifierDao.saveAll(qualifierEntities);
      log.debug("写入 {} 条 MeSH Qualifier 关联", qualifierEntities.size());
    }
  }

  // ========== Keywords 关联写入 ==========

  /// 写入关键词关联数据。
  ///
  /// 将展平后的关键词数据写入 `cat_publication_keyword` 表。
  /// 每条记录包含来源、关键词文本、主题标记和顺序。
  private void writeKeywordAssociations(List<PublicationImportResult> results) {
    List<PublicationKeywordEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasKeywords()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (KeywordData keyword : result.keywords()) {
        entities.add(
            PublicationKeywordEntity.of(
                publicationId,
                keyword.source(),
                keyword.term(),
                keyword.majorTopic(),
                keyword.keywordOrder()));
      }
    }

    if (!entities.isEmpty()) {
      keywordDao.saveAll(entities);
      log.debug("写入 {} 条关键词关联", entities.size());
    }
  }

  // ========== Funding 关联写入 ==========

  /// 写入资助信息关联数据。
  ///
  /// 将资助/基金信息写入 `cat_publication_funding` 表。
  /// 包含匹配的机构 ID、项目编号、原始数据和数据来源。
  private void writeFundingAssociations(List<PublicationImportResult> results) {
    List<PublicationFundingEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasFunding()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (FundingData funding : result.funding()) {
        entities.add(
            PublicationFundingEntity.builder()
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
      fundingDao.saveAll(entities);
      log.debug("写入 {} 条资助信息关联", entities.size());
    }
  }

  // ========== PublicationType 关联写入 ==========

  /// 写入出版类型关联数据。
  ///
  /// 将出版类型（如 Journal Article、Review）写入 `cat_publication_type` 表。
  /// 每条记录包含类型标识符、文本描述和词表来源。
  private void writePublicationTypeAssociations(List<PublicationImportResult> results) {
    List<PublicationTypeEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasPublicationTypes()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (PublicationTypeData type : result.publicationTypes()) {
        entities.add(
            PublicationTypeEntity.of(
                publicationId,
                type.typeId(),
                type.typeValue(),
                type.vocabularySource(),
                type.typeOrder()));
      }
    }

    if (!entities.isEmpty()) {
      typeDao.saveAll(entities);
      log.debug("写入 {} 条出版类型关联", entities.size());
    }
  }

  // ========== SupplMesh 关联写入 ==========

  /// 写入补充 MeSH 概念关联数据。
  ///
  /// 将补充 MeSH 概念（SupplMeshNameList）写入 `cat_publication_suppl_mesh` 表。
  /// 每条记录包含 SCR UI 和顺序，关联到 cat_mesh_scr 主数据表。
  private void writeSupplMeshAssociations(List<PublicationImportResult> results) {
    List<PublicationSupplMeshEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasSupplMeshNames()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (SupplMeshData supplMesh : result.supplMeshNames()) {
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
      supplMeshDao.saveAll(entities);
      log.debug("写入 {} 条补充 MeSH 概念关联", entities.size());
    }
  }

  // ========== AlternativeAbstract 关联写入 ==========

  /// 写入翻译摘要关联数据。
  ///
  /// 将 OtherAbstract 数据写入 `cat_publication_alternative_abstract` 表。
  /// 每条记录包含语言代码、翻译类型、摘要文本和版权信息。
  ///
  /// **Type 映射规则**：
  ///
  /// - Publisher → OFFICIAL（官方翻译）
  /// - plain-language-summary → PROFESSIONAL（专业翻译）
  /// - AIMSHP/KIEML/NASA 等 → PROFESSIONAL（专业机构翻译）
  /// - 其他 → OFFICIAL（默认假设为官方翻译）
  private void writeAlternativeAbstractAssociations(List<PublicationImportResult> results) {
    List<PublicationAlternativeAbstractEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasAlternativeAbstracts()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (AlternativeAbstractData altAbstract : result.alternativeAbstracts()) {
        String translationType = mapAbstractTypeToTranslationType(altAbstract.abstractType());
        boolean isOfficial = "Publisher".equalsIgnoreCase(altAbstract.abstractType());

        PublicationAlternativeAbstractEntity entity = new PublicationAlternativeAbstractEntity();
        entity.setId(SnowflakeIdGenerator.getId());
        entity.setPublicationId(publicationId);
        entity.setLanguageCode(altAbstract.languageCode());
        entity.setPlainText(altAbstract.plainText());
        entity.setTranslationType(translationType);
        entity.setIsOfficial(isOfficial);
        entity.setOrderNum(altAbstract.abstractOrder());

        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      alternativeAbstractDao.saveAll(entities);
      log.debug("写入 {} 条翻译摘要关联", entities.size());
    }
  }

  /// 将 PubMed OtherAbstract Type 映射为 TranslationType 代码。
  ///
  /// @param abstractType PubMed OtherAbstract 的 Type 属性值
  /// @return TranslationType 枚举的 code 值
  private String mapAbstractTypeToTranslationType(String abstractType) {
    if (abstractType == null || abstractType.isBlank()) {
      return "official"; // 默认为官方翻译
    }

    String normalizedType = abstractType.trim().toLowerCase();

    // Publisher 类型 → 官方翻译
    if ("publisher".equals(normalizedType)) {
      return "official";
    }

    // plain-language 系列 → 专业翻译
    if (normalizedType.contains("plain-language")) {
      return "professional";
    }

    // 专业机构翻译（AIMSHP, KIEML, NASA 等）→ 专业翻译
    if ("aimshp".equals(normalizedType)
        || "kieml".equals(normalizedType)
        || "nasa".equals(normalizedType)) {
      return "professional";
    }

    // 其他未知类型默认为官方翻译（保守处理）
    return "official";
  }

  // ========== Date 关联写入 ==========

  /// 写入日期关联数据。
  ///
  /// 将文献生命周期中的各类日期写入 `cat_publication_date` 表。
  /// 支持不完整日期（仅年份、年月、完整日期三种精度）。
  ///
  /// **写入字段**：
  ///
  /// - `publication_id` 文献 ID
  /// - `date_type` 日期类型（received/accepted/published 等）
  /// - `year/month/day` 分别存储年月日
  /// - `date_value` 完整日期时填充 LocalDate
  /// - `date_precision` 精度（year/month/day）
  /// - `is_primary` 是否主要日期
  /// - `order_num` 顺序号
  private void writeDateAssociations(List<PublicationImportResult> results) {
    List<PublicationDateEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasDates()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (PublicationDateData dateData : result.dates()) {
        PublicationDateEntity entity = new PublicationDateEntity();
        entity.setId(SnowflakeIdGenerator.getId());
        entity.setPublicationId(publicationId);
        entity.setDateType(dateData.dateType());
        entity.setYear(dateData.year());
        entity.setMonth(dateData.month());
        entity.setDay(dateData.day());
        entity.setDatePrecision(dateData.datePrecision());
        entity.setSeason(dateData.season());
        entity.setDateString(dateData.dateString());
        entity.setIsPrimary(dateData.isPrimary());
        entity.setOrderNum(dateData.orderNum());

        // 完整日期时填充 dateValue
        if ("day".equals(dateData.datePrecision())
            && dateData.month() != null
            && dateData.day() != null) {
          entity.setDateValue(LocalDate.of(dateData.year(), dateData.month(), dateData.day()));
        }

        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      dateDao.saveAll(entities);
      log.debug("写入 {} 条日期关联", entities.size());
    }
  }

  // ========== Identifier 关联写入 ==========

  /// 写入标识符关联数据。
  ///
  /// 将扩展标识符（PMC、PII、ARXIV 等）写入 `cat_publication_identifier` 表。
  /// 主要标识符（PMID、DOI）已冗余存储在主表中，不在此处重复写入。
  ///
  /// **写入字段**：
  ///
  /// - `publication_id` 文献 ID
  /// - `type` 标识符类型枚举
  /// - `value` 标识符值
  /// - `source` 标识符来源
  private void writeIdentifierAssociations(List<PublicationImportResult> results) {
    List<PublicationIdentifierEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      List<PublicationIdentifier> identifiers = result.publication().getExtendedIdentifiers();
      if (identifiers.isEmpty()) {
        continue;
      }

      Long publicationId = result.publication().getId().value();

      for (PublicationIdentifier identifier : identifiers) {
        PublicationIdentifierEntity entity = new PublicationIdentifierEntity();
        entity.setId(SnowflakeIdGenerator.getId());
        entity.setPublicationId(publicationId);
        entity.setType(identifier.type());
        entity.setValue(identifier.value());
        entity.setSource(identifier.source());
        entities.add(entity);
      }
    }

    if (!entities.isEmpty()) {
      identifierDao.saveAll(entities);
      log.debug("写入 {} 条标识符关联", entities.size());
    }
  }

  // ========== Abstract 关联写入 ==========

  /// 写入摘要关联数据。
  ///
  /// 将文献摘要写入 `cat_publication_abstract` 表。
  /// 摘要与文献是 1:1 关系，一篇文献最多有一条摘要记录。
  ///
  /// **写入字段**：
  ///
  /// - `publication_id` 文献 ID
  /// - `plain_text` 纯文本摘要
  /// - `structured_sections` 结构化摘要（JSON 格式）
  /// - `copyright` 版权信息
  /// - `abstract_type` 摘要类型
  private void writeAbstractAssociations(List<PublicationImportResult> results) {
    List<PublicationAbstractEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      PublicationAggregate publication = result.publication();

      // 只有有摘要内容时才写入
      if (!publication.hasAbstract()) {
        continue;
      }

      Long publicationId = publication.getId().value();
      PublicationAbstract abstractContent = publication.getPublicationAbstract();

      // 使用 mapper 转换（处理 JSON 序列化）
      PublicationAbstractEntity entity = jpaMapper.toAbstractEntity(abstractContent, publicationId);
      entity.setId(SnowflakeIdGenerator.getId());
      entities.add(entity);
    }

    if (!entities.isEmpty()) {
      abstractDao.saveAll(entities);
      log.debug("写入 {} 条摘要", entities.size());
    }
  }

  // ========== Investigator 关联写入 ==========

  /// 写入研究者关联数据。
  ///
  /// **去重策略**：
  ///
  /// 1. 收集本批次所有 ORCID 和 dedupKey
  /// 2. 批量查询已存在的研究者（优先 ORCID 匹配，其次 dedupKey 匹配）
  /// 3. 不存在则创建新研究者记录
  /// 4. 创建文献-研究者关联记录
  ///
  /// **性能优化**：
  ///
  /// - 批量查询替代逐条查询
  /// - 批量插入研究者和关联记录
  private void writeInvestigatorAssociations(List<PublicationImportResult> results) {
    // 1. 收集研究者数据
    var collectedData = collectInvestigatorsFromResults(results);
    if (collectedData.dedupKeyToData().isEmpty()) {
      return;
    }

    // 2. 查询已存在的研究者
    var existingMapping = queryExistingInvestigators(collectedData.dedupKeyToData());

    // 3. 创建新研究者并建立 dedupKey → ID 映射
    var dedupKeyToInvestigatorId =
        createNewInvestigatorsIfNeeded(collectedData.dedupKeyToData(), existingMapping);

    // 4. 创建关联记录
    createInvestigatorAssociations(
        collectedData.publicationInvestigators(), dedupKeyToInvestigatorId);
  }

  /// 研究者收集结果。
  ///
  /// @param dedupKeyToData dedupKey → 研究者数据映射
  /// @param publicationInvestigators 文献 ID → 研究者列表映射
  private record InvestigatorCollectionResult(
      Map<String, InvestigatorData> dedupKeyToData,
      Map<Long, List<InvestigatorData>> publicationInvestigators) {}

  /// 研究者查询结果。
  ///
  /// @param byOrcid ORCID → 研究者 ID 映射
  /// @param byDedupKey dedupKey → 研究者 ID 映射
  private record ExistingInvestigatorMapping(
      Map<String, Long> byOrcid, Map<String, Long> byDedupKey) {}

  /// 从处理结果中收集研究者数据。
  private InvestigatorCollectionResult collectInvestigatorsFromResults(
      List<PublicationImportResult> results) {
    Map<String, InvestigatorData> dedupKeyToData = new LinkedHashMap<>();
    Map<Long, List<InvestigatorData>> publicationInvestigators = new LinkedHashMap<>();

    for (PublicationImportResult result : results) {
      if (!result.hasInvestigators()) {
        continue;
      }

      Long pubId = result.publication().getId().value();
      List<InvestigatorData> invList = new ArrayList<>();

      for (InvestigatorData inv : result.investigators()) {
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
      Map<String, InvestigatorData> dedupKeyToData) {
    // 按 ORCID 查询
    Set<String> orcids =
        dedupKeyToData.values().stream()
            .map(InvestigatorData::orcid)
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
      Map<String, InvestigatorData> dedupKeyToData, ExistingInvestigatorMapping existingMapping) {
    List<InvestigatorEntity> newInvestigators = new ArrayList<>();
    Map<String, Long> dedupKeyToInvestigatorId = new HashMap<>();

    for (Map.Entry<String, InvestigatorData> entry : dedupKeyToData.entrySet()) {
      String dedupKey = entry.getKey();
      InvestigatorData data = entry.getValue();

      // 优先 ORCID 匹配
      Long existingId = null;
      if (data.orcid() != null && !data.orcid().isBlank()) {
        existingId = existingMapping.byOrcid().get(data.orcid());
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
        newInvestigators.add(buildInvestigatorEntity(newId, data));
      }
    }

    if (!newInvestigators.isEmpty()) {
      investigatorDao.saveAll(newInvestigators);
      log.debug("创建 {} 条新研究者记录", newInvestigators.size());
    }

    return dedupKeyToInvestigatorId;
  }

  /// 创建文献-研究者关联记录。
  private void createInvestigatorAssociations(
      Map<Long, List<InvestigatorData>> publicationInvestigators,
      Map<String, Long> dedupKeyToInvestigatorId) {
    List<PublicationInvestigatorEntity> associations = new ArrayList<>();

    for (Map.Entry<Long, List<InvestigatorData>> entry : publicationInvestigators.entrySet()) {
      Long pubId = entry.getKey();

      for (InvestigatorData inv : entry.getValue()) {
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
      publicationInvestigatorDao.saveAll(associations);
      log.debug("写入 {} 条研究者关联", associations.size());
    }
  }

  /// 构建研究者实体。
  ///
  /// @param id 预分配的雪花 ID
  /// @param data 研究者数据
  /// @return 研究者实体
  private InvestigatorEntity buildInvestigatorEntity(Long id, InvestigatorData data) {
    return InvestigatorEntity.builder()
        .id(id)
        .lastName(data.lastName())
        .foreName(data.foreName())
        .initials(data.initials())
        .suffix(data.suffix())
        .orcid(data.orcid())
        .affiliationName(data.affiliationName())
        .dedupKey(data.dedupKey())
        .build();
  }

  // ========== PersonalNameSubject 关联写入 ==========

  /// 写入人物主题关联数据。
  ///
  /// 将文献的主题人物（传记类、历史类、纪念类文献的描述对象）
  /// 写入 `cat_publication_personal_name_subject` 表。
  ///
  /// **设计说明**：
  ///
  /// 与研究者不同，人物主题不需要去重：
  /// - 同一历史人物可能有多种名字拼写形式
  /// - 不同文献引用同一人物时可能使用不同的描述
  /// - 人物主题记录与文献绑定，不作为独立实体管理
  private void writePersonalNameSubjectAssociations(List<PublicationImportResult> results) {
    List<PublicationPersonalNameSubjectEntity> entities = new ArrayList<>();

    for (PublicationImportResult result : results) {
      if (!result.hasPersonalNameSubjects()) {
        continue;
      }

      Long pubId = result.publication().getId().value();

      for (PersonalNameSubjectData subject : result.personalNameSubjects()) {
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
      personalNameSubjectDao.saveAll(entities);
      log.debug("写入 {} 条人物主题关联", entities.size());
    }
  }
}
