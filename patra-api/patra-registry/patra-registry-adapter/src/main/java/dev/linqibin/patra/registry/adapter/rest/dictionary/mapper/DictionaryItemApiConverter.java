package dev.linqibin.patra.registry.adapter.rest.dictionary.mapper;

import dev.linqibin.patra.registry.adapter.rest.dictionary.response.DictionaryItemListResponse;
import dev.linqibin.patra.registry.adapter.rest.dictionary.response.DictionaryItemResponse;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemListResult;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryItemSummary;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// 字典项列表查询的 Domain → API 响应转换器。
///
/// 使用 MapStruct 将 {@link DictionaryItemListResult} 转换为 {@link DictionaryItemListResponse}。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DictionaryItemApiConverter {

  /// 转换字典项列表结果。
  ///
  /// @param result 领域层查询结果
  /// @return API 响应 DTO
  DictionaryItemListResponse toResponse(DictionaryItemListResult result);

  /// 转换单个字典项摘要。
  ///
  /// @param summary 领域层字典项摘要
  /// @return API 响应 DTO
  DictionaryItemResponse toResponse(DictionaryItemSummary summary);

  /// 转换字典项摘要列表。
  ///
  /// @param summaries 领域层字典项摘要列表
  /// @return API 响应 DTO 列表
  List<DictionaryItemResponse> toResponse(List<DictionaryItemSummary> summaries);
}
