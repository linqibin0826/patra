package dev.linqibin.patra.catalog.domain.model.read.venue;

import cn.hutool.core.lang.Assert;
import dev.linqibin.patra.catalog.domain.model.vo.venue.CitationMetrics;
import dev.linqibin.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import dev.linqibin.patra.catalog.domain.model.vo.venue.PublicationProfile;
import dev.linqibin.patra.catalog.domain.model.vo.venue.Society;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/// 期刊详情读模型。
///
/// @param id 期刊主键 ID
/// @param venueType 载体类型（JOURNAL/REPOSITORY/CONFERENCE/等）
/// @param title 期刊标题
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
/// @param openalexId OpenAlex Source ID（可空）
/// @param abbreviatedTitle 缩写标题（可空）
/// @param primaryLanguage 主要语言代码（可空）
/// @param countryCode 国家编码（可空）
/// @param publicationProfile 出版概况（可空）
/// @param citationMetrics 引用指标（可空）
/// @param openAccess 开放获取信息（可空）
/// @param affiliatedSocieties 关联学会列表（可空）
/// @param meshHeadings MeSH 主题词列表
/// @param relations 期刊关联关系列表
/// @param indexingHistory 索引历史列表
/// @param latestRating 最新评级摘要（JCR/CAS/Scopus/预警，可空）
/// @param lastSyncedAt 最后同步时间（可空）
/// @param createdAt 创建时间
/// @param updatedAt 更新时间
@Builder
public record VenueDetailReadModel(
    Long id,
    String venueType,
    String title,
    String issnL,
    String nlmId,
    String openalexId,
    String abbreviatedTitle,
    String primaryLanguage,
    String countryCode,
    PublicationProfile publicationProfile,
    CitationMetrics citationMetrics,
    OpenAccessInfo openAccess,
    List<Society> affiliatedSocieties,
    List<MeshHeading> meshHeadings,
    List<VenueRelationItem> relations,
    List<IndexingHistoryItem> indexingHistory,
    VenueLatestRating latestRating,
    Instant lastSyncedAt,
    Instant createdAt,
    Instant updatedAt) {

  /// 构造期刊详情读模型并执行参数校验。
  public VenueDetailReadModel {
    Assert.notNull(id, "期刊 ID 不能为空");
    Assert.notBlank(venueType, "载体类型不能为空");
    Assert.notBlank(title, "期刊标题不能为空");
    Assert.notNull(createdAt, "创建时间不能为空");
    Assert.notNull(updatedAt, "更新时间不能为空");
    // 防御性拷贝：确保集合不可变
    affiliatedSocieties =
        affiliatedSocieties != null ? List.copyOf(affiliatedSocieties) : List.of();
    meshHeadings = meshHeadings != null ? List.copyOf(meshHeadings) : List.of();
    relations = relations != null ? List.copyOf(relations) : List.of();
    indexingHistory = indexingHistory != null ? List.copyOf(indexingHistory) : List.of();
  }

  /// MeSH 主题词条目。
  ///
  /// @param descriptorName 描述符名称
  /// @param descriptorUi 描述符唯一标识符
  /// @param isMajorTopic 是否主要主题
  /// @param qualifierName 限定符名称（可空）
  /// @param qualifierUi 限定符唯一标识符（可空）
  public record MeshHeading(
      String descriptorName,
      String descriptorUi,
      Boolean isMajorTopic,
      String qualifierName,
      String qualifierUi) {}

  /// 期刊关联关系条目。
  ///
  /// @param relatedVenueId 关联期刊 ID（可空）
  /// @param relatedTitle 关联期刊标题
  /// @param relationType 关系类型
  /// @param effectiveDate 生效日期（可空）
  /// @param notes 备注说明（可空）
  public record VenueRelationItem(
      Long relatedVenueId,
      String relatedTitle,
      String relationType,
      LocalDate effectiveDate,
      String notes) {}

  /// 索引历史条目。
  ///
  /// @param indexingSource 索引来源
  /// @param currentlyIndexed 当前是否被索引
  /// @param indexingTreatment 索引处理方式（可空）
  /// @param startYear 开始年份（可空）
  /// @param endYear 结束年份（可空）
  /// @param startVolume 开始卷号（可空）
  /// @param startIssue 开始期号（可空）
  /// @param endVolume 结束卷号（可空）
  /// @param endIssue 结束期号（可空）
  public record IndexingHistoryItem(
      String indexingSource,
      Boolean currentlyIndexed,
      String indexingTreatment,
      Integer startYear,
      Integer endYear,
      String startVolume,
      String startIssue,
      String endVolume,
      String endIssue) {}
}
