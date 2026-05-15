package dev.linqibin.patra.registry.adapter.rest.converter;

import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveItemResp;
import dev.linqibin.patra.registry.api.dto.dict.DictionaryResolveResp;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveItemQuery;
import dev.linqibin.patra.registry.domain.model.read.dictionary.DictionaryResolveQuery;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/// 字典解析查询 DTO 到 API 响应 DTO 的转换器。
///
/// 使用 MapStruct 自动生成转换代码,将读侧查询对象转换为外部 API 契约 DTO。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DictionaryApiConverter {

  /// 转换批量解析结果。
  ///
  /// @param query 批量解析查询对象
  /// @return 批量解析响应 DTO
  DictionaryResolveResp toResp(DictionaryResolveQuery query);

  /// 转换单个解析项。
  ///
  /// @param query 单个解析项查询对象
  /// @return 单个解析项响应 DTO
  DictionaryResolveItemResp toResp(DictionaryResolveItemQuery query);

  /// 转换解析项列表。
  ///
  /// @param queries 解析项查询对象列表
  /// @return 解析项响应 DTO 列表
  List<DictionaryResolveItemResp> toResp(List<DictionaryResolveItemQuery> queries);
}
