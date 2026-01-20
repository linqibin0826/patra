package com.patra.catalog.infra.adapter.batch.publication;

import com.patra.catalog.domain.model.aggregate.PublicationAggregate;
import com.patra.catalog.domain.port.repository.PublicationRepository;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.FundingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.KeywordData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.MeshHeadingData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.PublicationTypeData;
import com.patra.catalog.infra.adapter.batch.publication.PublicationImportResult.QualifierData;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationFundingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationKeywordDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshHeadingDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationMeshQualifierDao;
import com.patra.catalog.infra.adapter.persistence.dao.PublicationTypeDao;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationFundingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationKeywordEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshHeadingEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationMeshQualifierEntity;
import com.patra.catalog.infra.adapter.persistence.entity.PublicationTypeEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
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
}
