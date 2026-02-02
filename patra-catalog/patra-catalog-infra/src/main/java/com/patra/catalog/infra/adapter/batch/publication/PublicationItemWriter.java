package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.AlternativeAbstractData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationDateData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.SupplMeshData;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationAlternativeAbstractDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationDateDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationFundingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationKeywordDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshHeadingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshQualifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationSupplMeshDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationTypeDao;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationAlternativeAbstractEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationDateEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationFundingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationKeywordEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshHeadingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshQualifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationSupplMeshEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationTypeEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
    writeMeshAssociations(results);
    writeKeywordAssociations(results);
    writeFundingAssociations(results);
    writePublicationTypeAssociations(results);
    writeSupplMeshAssociations(results);
    writeAlternativeAbstractAssociations(results);
    writeDateAssociations(results);
  }

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
}
