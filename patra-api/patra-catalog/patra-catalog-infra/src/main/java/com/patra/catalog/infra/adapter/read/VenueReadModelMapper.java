package com.patra.catalog.infra.adapter.read;

import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import com.patra.catalog.infra.persistence.entity.VenueEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
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
  @Mapping(source = "cas.majorQuartile", target = "casMajorQuartile")
  @Mapping(source = "cas.isTopJournal", target = "casTopJournal")
  @Mapping(source = "scopus.citeScore", target = "citeScore")
  @Mapping(source = "scopus.quartile", target = "citeScoreQuartile")
  @Mapping(target = "warningListStatus", ignore = true)
  VenueSummaryReadModel toReadModel(
      VenueEntity entity, JcrRatingEntity jcr, CasRatingEntity cas, ScopusRatingEntity scopus);

  /// 将 VenueEntity 转换为详情读模型。
  ///
  /// @param entity Venue JPA 实体
  /// @return Venue 详情读模型
  VenueDetailReadModel toDetailReadModel(VenueEntity entity);
}
