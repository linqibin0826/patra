package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.enums.CasWarningLevel;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel.IndexingHistoryItem;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel.MeshHeading;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel.VenueRelationItem;
import com.patra.catalog.domain.model.read.venue.VenueLatestRating;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.CasWarningEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import com.patra.catalog.infra.persistence.entity.VenueIndexingHistoryEntity;
import com.patra.catalog.infra.persistence.entity.VenueMeshEntity;
import com.patra.catalog.infra.persistence.entity.VenueRelationEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

/// Venue 读模型 MapStruct 转换器。
///
/// 将 JPA Entity 转换为 CQRS 读端的 ReadModel。
/// 列表摘要支持多源映射：VenueEntity + JcrRatingEntity + CasRatingEntity + ScopusRatingEntity。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueReadModelMapper {

  /// 将 VenueEntity + JCR/CAS/Scopus 评级转换为列表摘要读模型。
  ///
  /// @param entity Venue JPA 实体
  /// @param jcr 最新 JCR 评级（可为 null）
  /// @param cas 最新 CAS 评级（可为 null）
  /// @param scopus 最新 Scopus 评级（可为 null）
  /// @return Venue 摘要读模型
  @Mapping(source = "entity.id", target = "id")
  @Mapping(source = "entity.title", target = "title")
  @Mapping(source = "entity.countryCode", target = "countryCode")
  @Mapping(source = "entity.imageObjectKey", target = "imageObjectKey")
  @Mapping(source = "entity.citationMetrics.hIndex", target = "hIndex")
  @Mapping(source = "entity.openAccess.isOa", target = "isOa")
  @Mapping(source = "jcr.jifQuartile", target = "jifQuartile")
  @Mapping(source = "jcr.researchDirection", target = "researchDirection")
  @Mapping(source = "jcr.impactFactor", target = "impactFactor")
  @Mapping(source = "jcr.collection", target = "collection")
  @Mapping(source = "cas.majorQuartile", target = "casMajorQuartile")
  @Mapping(source = "cas.isTopJournal", target = "casTopJournal")
  @Mapping(source = "scopus.citeScore", target = "citeScore")
  @Mapping(source = "scopus.quartile", target = "citeScoreQuartile")
  VenueSummaryReadModel toReadModel(
      VenueEntity entity, JcrRatingEntity jcr, CasRatingEntity cas, ScopusRatingEntity scopus);

  /// 将 VenueEntity + 评级数据 + MeSH/关系/索引历史转换为详情读模型。
  ///
  /// @param entity Venue JPA 实体
  /// @param latestRating 最新评级摘要（可为 null）
  /// @param meshEntities MeSH 主题词实体列表
  /// @param relationEntities 关联关系实体列表
  /// @param indexingEntities 索引历史实体列表
  /// @return Venue 详情读模型
  @Mapping(source = "entity.id", target = "id")
  @Mapping(source = "entity.venueType", target = "venueType")
  @Mapping(source = "entity.title", target = "title")
  @Mapping(source = "entity.issnL", target = "issnL")
  @Mapping(source = "entity.nlmId", target = "nlmId")
  @Mapping(source = "entity.openalexId", target = "openalexId")
  @Mapping(source = "entity.abbreviatedTitle", target = "abbreviatedTitle")
  @Mapping(source = "entity.primaryLanguage", target = "primaryLanguage")
  @Mapping(source = "entity.countryCode", target = "countryCode")
  @Mapping(source = "entity.publicationProfile", target = "publicationProfile")
  @Mapping(source = "entity.citationMetrics", target = "citationMetrics")
  @Mapping(source = "entity.openAccess", target = "openAccess")
  @Mapping(source = "entity.affiliatedSocieties", target = "affiliatedSocieties")
  @Mapping(source = "meshEntities", target = "meshHeadings")
  @Mapping(source = "relationEntities", target = "relations")
  @Mapping(source = "indexingEntities", target = "indexingHistory")
  @Mapping(source = "latestRating", target = "latestRating")
  @Mapping(source = "entity.lastSyncedAt", target = "lastSyncedAt")
  @Mapping(source = "entity.createdAt", target = "createdAt")
  @Mapping(source = "entity.updatedAt", target = "updatedAt")
  VenueDetailReadModel toDetailReadModel(
      VenueEntity entity,
      VenueLatestRating latestRating,
      List<VenueMeshEntity> meshEntities,
      List<VenueRelationEntity> relationEntities,
      List<VenueIndexingHistoryEntity> indexingEntities);

  /// 将 VenueMeshEntity 转换为 MeshHeading 读模型。
  ///
  /// @param entity MeSH 主题词实体
  /// @return MeSH 主题词读模型
  MeshHeading toMeshHeading(VenueMeshEntity entity);

  /// 将 VenueRelationEntity 转换为 VenueRelationItem 读模型。
  ///
  /// @param entity 关联关系实体
  /// @return 关联关系读模型
  VenueRelationItem toRelationItem(VenueRelationEntity entity);

  /// 将 VenueIndexingHistoryEntity 转换为 IndexingHistoryItem 读模型。
  ///
  /// @param entity 索引历史实体
  /// @return 索引历史读模型
  IndexingHistoryItem toIndexingHistoryItem(VenueIndexingHistoryEntity entity);

  /// 将 JCR/CAS/Scopus 评级和 CAS 预警转换为最新评级摘要。
  ///
  /// 各参数均可为 null（无对应数据源时）。
  ///
  /// @param jcr 最新 JCR 评级（可为 null）
  /// @param cas 最新 CAS 评级（可为 null）
  /// @param scopus 最新 Scopus 评级（可为 null）
  /// @param warning 最新 CAS 预警（可为 null）
  /// @return 评级摘要（全部参数为 null 时返回 null）
  @Mapping(source = "jcr.year", target = "jcrYear")
  @Mapping(source = "jcr.impactFactor", target = "impactFactor")
  @Mapping(source = "jcr.jifQuartile", target = "jifQuartile")
  @Mapping(source = "jcr.jifRank", target = "jifRank")
  @Mapping(source = "jcr.jifPercentile", target = "jifPercentile")
  @Mapping(source = "jcr.wosOverallQuartile", target = "wosOverallQuartile")
  @Mapping(source = "jcr.collection", target = "collection")
  @Mapping(source = "jcr.selfCitationRate", target = "selfCitationRate")
  @Mapping(source = "jcr.researchDirection", target = "researchDirection")
  @Mapping(source = "jcr.jciValue", target = "jciValue")
  @Mapping(source = "jcr.jciQuartile", target = "jciQuartile")
  @Mapping(source = "cas.year", target = "casYear")
  @Mapping(source = "cas.edition", target = "casEdition")
  @Mapping(source = "cas.majorCategory", target = "majorCategory")
  @Mapping(source = "cas.majorQuartile", target = "majorQuartile")
  @Mapping(source = "cas.minorSubject", target = "minorSubject")
  @Mapping(source = "cas.minorQuartile", target = "minorQuartile")
  @Mapping(source = "cas.isTopJournal", target = "isTopJournal")
  @Mapping(source = "cas.isReviewJournal", target = "isReviewJournal")
  @Mapping(source = "scopus.year", target = "scopusYear")
  @Mapping(source = "scopus.citeScore", target = "citeScore")
  @Mapping(source = "scopus.sjr", target = "sjr")
  @Mapping(source = "scopus.snip", target = "snip")
  @Mapping(source = "scopus.quartile", target = "citeScoreQuartile")
  @Mapping(source = "scopus.percentile", target = "citeScorePercentile")
  @Mapping(source = "warning.inWarningList", target = "inWarningList")
  @Mapping(
      source = "warning.warningLevel",
      target = "warningLevel",
      qualifiedByName = "warningLevelToString")
  VenueLatestRating toLatestRating(
      JcrRatingEntity jcr,
      CasRatingEntity cas,
      ScopusRatingEntity scopus,
      CasWarningEntity warning);

  /// 将 CasWarningLevel 枚举转换为字符串代码。
  ///
  /// @param level 预警级别枚举（可为 null）
  /// @return 预警级别代码（如 "high"、"medium"、"low"），null 输入返回 null
  @Named("warningLevelToString")
  default String warningLevelToString(CasWarningLevel level) {
    return level != null ? level.getCode() : null;
  }
}
