package dev.patra.catalog.api.venue.dto;

import java.util.List;

/// 期刊批量解析响应：每个去重后的请求键一行，按请求顺序。
///
/// @param items 解析结果
/// @author linqibin
/// @since 0.1.0
public record VenueResolveResp(List<VenueResolutionResp> items) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public VenueResolveResp {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 创建响应。
  ///
  /// @param items 解析结果
  /// @return 响应
  public static VenueResolveResp of(List<VenueResolutionResp> items) {
    return new VenueResolveResp(items);
  }
}
