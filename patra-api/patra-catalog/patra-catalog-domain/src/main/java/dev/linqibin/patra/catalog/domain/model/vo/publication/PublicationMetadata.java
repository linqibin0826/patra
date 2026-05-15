package dev.linqibin.patra.catalog.domain.model.vo.publication;

import cn.hutool.core.util.StrUtil;
import dev.linqibin.patra.catalog.domain.model.enums.IndexingStatus;
import dev.linqibin.patra.catalog.domain.model.enums.QualityScore;
import dev.linqibin.patra.catalog.domain.model.enums.ReviewStatus;
import dev.linqibin.patra.common.enums.ProvenanceCode;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/// 文献元数据值对象。
///
/// 封装文献的元数据信息：索引状态、质量评分、数据溯源、审核状态等。
///
/// **与主文献 1:1 关系**：
///
/// 元数据是文献的补充数据，通过 Repository 独立管理：
///
/// - 使用 `PublicationRepository.replaceMetadataBatch()` 批量替换
/// - 不在聚合根内直接维护
///
/// **主要用途**：
///
/// - **索引追踪**：记录 PubMed/MEDLINE 索引状态
/// - **质量评估**：数据完整性和准确性评分
/// - **数据溯源**：记录数据来源和导入批次
/// - **审核工作流**：人工审核状态追踪
///
/// 使用示例：
///
/// ```java
/// PublicationMetadata metadata = PublicationMetadata.builder()
///     .indexingStatus(IndexingStatus.MEDLINE)
///     .dataSource(ProvenanceCode.PUBMED)
///     .importBatch("2024-03-15_PUBMED_BASELINE")
///     .importDate(Instant.now())
///     .qualityScore(QualityScore.A)
///     .completenessScore(QualityScore.B)
///     .hasFullText(true)
///     .build();
/// ```
///
/// @param indexingStatus 索引状态
/// @param indexingMethod 索引方法（Automated/Curated/In-Data-Review）
/// @param indexedDate 索引日期
/// @param dataSource 数据来源
/// @param importBatch 导入批次标识
/// @param importDate 导入时间
/// @param qualityScore 质量评分
/// @param completenessScore 完整性评分
/// @param hasFullText 是否有全文
/// @param fullTextUrl 全文链接
/// @param reviewStatus 审核状态
/// @param reviewDate 审核日期
/// @param reviewer 审核人
/// @param validationErrors 验证错误列表
/// @param processingNotes 处理注释列表
/// @param owner 数据记录所有者（NLM, NASA, PIP 等）
/// @param citationSubset 引用子集标识（IM, AIM 等）
/// @author linqibin
/// @since 0.1.0
@Builder(toBuilder = true)
public record PublicationMetadata(
    IndexingStatus indexingStatus,
    String indexingMethod,
    LocalDate indexedDate,
    ProvenanceCode dataSource,
    String importBatch,
    Instant importDate,
    String owner,
    String citationSubset,
    QualityScore qualityScore,
    QualityScore completenessScore,
    boolean hasFullText,
    String fullTextUrl,
    ReviewStatus reviewStatus,
    LocalDate reviewDate,
    String reviewer,
    List<String> validationErrors,
    List<String> processingNotes)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：处理防御性拷贝。
  public PublicationMetadata {
    // 防御性拷贝：确保列表不可变
    validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
    processingNotes = processingNotes != null ? List.copyOf(processingNotes) : List.of();
  }

  /// 创建空元数据。
  ///
  /// @return 无任何字段设置的元数据
  public static PublicationMetadata empty() {
    return PublicationMetadata.builder().build();
  }

  /// 创建基本元数据（仅数据来源和导入信息）。
  ///
  /// @param dataSource 数据来源
  /// @param importBatch 导入批次
  /// @return 基本元数据
  public static PublicationMetadata ofImport(ProvenanceCode dataSource, String importBatch) {
    return PublicationMetadata.builder()
        .dataSource(dataSource)
        .importBatch(importBatch)
        .importDate(Instant.now())
        .build();
  }

  /// 判断是否已完成索引。
  ///
  /// @return true 如果索引状态表示已索引
  public boolean isIndexed() {
    return indexingStatus != null && indexingStatus.isIndexed();
  }

  /// 判断是否有 MeSH 主题词。
  ///
  /// @return true 如果为 MEDLINE 索引状态
  public boolean hasMeshTerms() {
    return indexingStatus != null && indexingStatus.hasMeshTerms();
  }

  /// 判断质量是否合格。
  ///
  /// @return true 如果质量评分为 C 及以上
  public boolean isQualityPassing() {
    return qualityScore != null && qualityScore.isPassing();
  }

  /// 判断数据完整性是否合格。
  ///
  /// @return true 如果完整性评分为 C 及以上
  public boolean isCompletenessPassing() {
    return completenessScore != null && completenessScore.isPassing();
  }

  /// 判断是否通过审核。
  ///
  /// @return true 如果审核状态为 APPROVED
  public boolean isReviewPassed() {
    return reviewStatus != null && reviewStatus.isPassed();
  }

  /// 判断是否有验证错误。
  ///
  /// @return true 如果存在验证错误
  public boolean hasValidationErrors() {
    return !validationErrors.isEmpty();
  }

  /// 判断是否有处理注释。
  ///
  /// @return true 如果存在处理注释
  public boolean hasProcessingNotes() {
    return !processingNotes.isEmpty();
  }

  /// 判断是否有全文链接。
  ///
  /// @return true 如果有全文 URL
  public boolean hasFullTextUrl() {
    return StrUtil.isNotBlank(fullTextUrl);
  }

  /// 判断是否有导入批次信息。
  ///
  /// @return true 如果有导入批次
  public boolean hasImportBatch() {
    return StrUtil.isNotBlank(importBatch);
  }

  /// 获取验证错误数量。
  ///
  /// @return 错误数量
  public int getValidationErrorCount() {
    return validationErrors.size();
  }

  /// 获取处理注释数量。
  ///
  /// @return 注释数量
  public int getProcessingNoteCount() {
    return processingNotes.size();
  }
}
