package com.patra.catalog.adapter.rest.publication.mapper;

import com.patra.catalog.adapter.rest.publication.request.PublicationListRequest;
import com.patra.catalog.adapter.rest.publication.response.PublicationDetailResponse;
import com.patra.catalog.adapter.rest.publication.response.PublicationItemResponse;
import com.patra.catalog.app.usecase.publication.query.dto.PublicationListQuery;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.AbstractInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.IdentifierInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.KeywordInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo;
import com.patra.catalog.domain.model.read.publication.PublicationDetailReadModel.MeshHeadingInfo.MeshQualifierInfo;
import com.patra.catalog.domain.model.read.publication.PublicationSummaryReadModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// Publication 查询 API 转换器。
///
/// 将 Adapter 层 DTO 与 Application/Domain 读模型进行转换。
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicationApiConverter {

  /// 将列表请求转换为应用层查询参数。
  ///
  /// `venueInstanceId` 和 `sortBy` 由 VenueController 的实例文献端点单独构建，
  /// 通用文献列表请求不包含这两个字段。
  ///
  /// @param request Publication 列表请求
  /// @return 应用层查询参数
  @Mapping(target = "venueInstanceId", ignore = true)
  @Mapping(target = "sortBy", ignore = true)
  PublicationListQuery toQuery(PublicationListRequest request);

  /// 将 Publication 摘要读模型转换为 API 列表项响应。
  ///
  /// @param readModel Publication 摘要读模型
  /// @return API 列表项响应
  PublicationItemResponse toItemResponse(PublicationSummaryReadModel readModel);

  /// 将 Publication 详情读模型转换为 API 详情响应。
  ///
  /// @param readModel Publication 详情读模型
  /// @return API 详情响应
  PublicationDetailResponse toDetailResponse(PublicationDetailReadModel readModel);

  // ========== 嵌套 Record → DTO 转换方法 ==========

  /// 将 AbstractInfo 转换为 AbstractDto。
  ///
  /// @param info 摘要信息
  /// @return 摘要 DTO
  PublicationDetailResponse.AbstractDto toDto(AbstractInfo info);

  /// 将 IdentifierInfo 转换为 IdentifierDto。
  ///
  /// @param info 标识符信息
  /// @return 标识符 DTO
  PublicationDetailResponse.IdentifierDto toDto(IdentifierInfo info);

  /// 将 KeywordInfo 转换为 KeywordDto。
  ///
  /// @param info 关键词信息
  /// @return 关键词 DTO
  PublicationDetailResponse.KeywordDto toDto(KeywordInfo info);

  /// 将 MeshHeadingInfo 转换为 MeshHeadingDto。
  ///
  /// @param info MeSH 标引信息
  /// @return MeSH 标引 DTO
  PublicationDetailResponse.MeshHeadingDto toDto(MeshHeadingInfo info);

  /// 将 MeshQualifierInfo 转换为 MeshQualifierDto。
  ///
  /// @param info MeSH 限定词信息
  /// @return MeSH 限定词 DTO
  PublicationDetailResponse.MeshHeadingDto.MeshQualifierDto toDto(MeshQualifierInfo info);
}
