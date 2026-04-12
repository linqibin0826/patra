package com.patra.catalog.adapter.rest.venue.mapper;

import com.patra.catalog.adapter.rest.venue.request.VenueListRequest;
import com.patra.catalog.adapter.rest.venue.response.VenueDetailResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueItemResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueRatingHistoryResponse;
import com.patra.catalog.adapter.rest.venue.response.VenueStatsResponse;
import com.patra.catalog.app.usecase.venue.query.dto.VenueListQuery;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel.IndexingHistoryItem;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel.MeshHeading;
import com.patra.catalog.domain.model.read.venue.VenueDetailReadModel.VenueRelationItem;
import com.patra.catalog.domain.model.read.venue.VenueLatestRating;
import com.patra.catalog.domain.model.read.venue.VenueRatingHistoryReadModel;
import com.patra.catalog.domain.model.read.venue.VenueStatsReadModel;
import com.patra.catalog.domain.model.read.venue.VenueSummaryReadModel;
import com.patra.catalog.domain.model.vo.venue.CitationMetrics;
import com.patra.catalog.domain.model.vo.venue.HostOrganization;
import com.patra.catalog.domain.model.vo.venue.IndexingInfo;
import com.patra.catalog.domain.model.vo.venue.OpenAccessInfo;
import com.patra.catalog.domain.model.vo.venue.PublicationHistory;
import com.patra.catalog.domain.model.vo.venue.PublicationProfile;
import com.patra.catalog.domain.model.vo.venue.Society;
import com.patra.catalog.domain.model.vo.venue.VenueLanguages;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// Venue 查询 API 转换器。
///
/// 将 Adapter 层 DTO 与 Application/Domain 读模型进行转换。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueApiConverter {

  /// 将列表请求转换为应用层查询参数。
  ///
  /// @param request Venue 列表请求
  /// @return 应用层查询参数
  VenueListQuery toQuery(VenueListRequest request);

  /// 将 Venue 摘要读模型转换为 API 列表项响应。
  ///
  /// @param readModel Venue 摘要读模型
  /// @return API 列表项响应
  VenueItemResponse toItemResponse(VenueSummaryReadModel readModel);

  /// 将 Venue 详情读模型转换为 API 详情响应。
  ///
  /// @param readModel Venue 详情读模型
  /// @return API 详情响应
  VenueDetailResponse toDetailResponse(VenueDetailReadModel readModel);

  // ========== 嵌套值对象 → DTO 转换方法 ==========

  /// 将 CitationMetrics VO 转换为 CitationMetricsDto。
  ///
  /// @param vo 引用指标值对象
  /// @return 引用指标 DTO
  VenueDetailResponse.CitationMetricsDto toDto(CitationMetrics vo);

  /// 将 PublicationProfile VO 转换为 PublicationProfileDto。
  ///
  /// @param vo 出版概况值对象
  /// @return 出版概况 DTO
  VenueDetailResponse.PublicationProfileDto toDto(PublicationProfile vo);

  /// 将 OpenAccessInfo VO 转换为 OpenAccessDto。
  ///
  /// @param vo 开放获取信息值对象
  /// @return 开放获取信息 DTO
  VenueDetailResponse.OpenAccessDto toDto(OpenAccessInfo vo);

  /// 将 Society VO 转换为 SocietyDto。
  ///
  /// @param vo 关联学会值对象
  /// @return 关联学会 DTO
  VenueDetailResponse.SocietyDto toDto(Society vo);

  /// 将 PublicationHistory VO 转换为 PublicationHistoryDto。
  ///
  /// @param vo 出版历史值对象
  /// @return 出版历史 DTO
  VenueDetailResponse.PublicationProfileDto.PublicationHistoryDto toDto(PublicationHistory vo);

  /// 将 VenueLanguages VO 转换为 VenueLanguagesDto。
  ///
  /// @param vo 语言信息值对象
  /// @return 语言信息 DTO
  VenueDetailResponse.PublicationProfileDto.VenueLanguagesDto toDto(VenueLanguages vo);

  /// 将 HostOrganization VO 转换为 HostOrganizationDto。
  ///
  /// @param vo 宿主机构值对象
  /// @return 宿主机构 DTO
  VenueDetailResponse.PublicationProfileDto.HostOrganizationDto toDto(HostOrganization vo);

  /// 将 IndexingInfo VO 转换为 IndexingInfoDto。
  ///
  /// @param vo 索引信息值对象
  /// @return 索引信息 DTO
  VenueDetailResponse.PublicationProfileDto.IndexingInfoDto toDto(IndexingInfo vo);

  /// 将 VenueLatestRating 读模型转换为 LatestRatingDto。
  ///
  /// @param latestRating 最新评级摘要读模型
  /// @return 最新评级 DTO
  VenueDetailResponse.LatestRatingDto toDto(VenueLatestRating latestRating);

  /// 将 MeshHeading 读模型转换为 MeshHeadingDto。
  ///
  /// @param meshHeading MeSH 主题词读模型
  /// @return MeSH 主题词 DTO
  VenueDetailResponse.MeshHeadingDto toDto(MeshHeading meshHeading);

  /// 将 VenueRelationItem 读模型转换为 VenueRelationDto。
  ///
  /// @param relationItem 关联关系读模型
  /// @return 关联关系 DTO
  VenueDetailResponse.VenueRelationDto toDto(VenueRelationItem relationItem);

  /// 将 IndexingHistoryItem 读模型转换为 IndexingHistoryDto。
  ///
  /// @param indexingHistoryItem 索引历史读模型
  /// @return 索引历史 DTO
  VenueDetailResponse.IndexingHistoryDto toDto(IndexingHistoryItem indexingHistoryItem);

  /// 将评级历史读模型转换为 API 响应。
  ///
  /// @param readModel 评级历史读模型
  /// @return 评级历史响应 DTO
  VenueRatingHistoryResponse toRatingHistoryResponse(VenueRatingHistoryReadModel readModel);

  /// 将发文统计读模型转换为 API 响应。
  ///
  /// @param readModel 发文统计读模型
  /// @return 发文统计响应 DTO
  VenueStatsResponse toStatsResponse(VenueStatsReadModel readModel);

  /// 将 YearStats 读模型转换为 YearStatsDto。
  ///
  /// @param yearStats 年度统计记录
  /// @return 年度统计 DTO
  VenueStatsResponse.YearStatsDto toDto(VenueStatsReadModel.YearStats yearStats);

  /// 将 JcrRecord 读模型转换为 JcrRatingDto。
  ///
  /// @param record JCR 评级历史记录
  /// @return JCR 评级 DTO
  VenueRatingHistoryResponse.JcrRatingDto toDto(VenueRatingHistoryReadModel.JcrRecord record);

  /// 将 CasRecord 读模型转换为 CasRatingDto。
  ///
  /// @param record CAS 分区历史记录
  /// @return CAS 分区 DTO
  VenueRatingHistoryResponse.CasRatingDto toDto(VenueRatingHistoryReadModel.CasRecord record);

  /// 将 ScopusRecord 读模型转换为 ScopusRatingDto。
  ///
  /// @param record Scopus 指标历史记录
  /// @return Scopus 指标 DTO
  VenueRatingHistoryResponse.ScopusRatingDto toDto(VenueRatingHistoryReadModel.ScopusRecord record);

  /// 将 WarningRecord 读模型转换为 WarningDto。
  ///
  /// @param record CAS 预警历史记录
  /// @return CAS 预警 DTO
  VenueRatingHistoryResponse.WarningDto toDto(VenueRatingHistoryReadModel.WarningRecord record);
}
