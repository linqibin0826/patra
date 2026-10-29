package dev.patra.catalog.api.publication.dto;

import java.util.List;

/// 文献批量解析响应：每个去重后的请求键一行，按请求顺序。
///
/// @param items 解析结果
/// @author linqibin
/// @since 0.1.0
public record PublicationResolveResp(List<PublicationResolutionResp> items) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public PublicationResolveResp {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 创建响应。
  ///
  /// @param items 解析结果
  /// @return 响应
  public static PublicationResolveResp of(List<PublicationResolutionResp> items) {
    return new PublicationResolveResp(items);
  }
}
