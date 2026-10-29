package dev.patra.catalog.adapter.portal.rest.response;

import java.util.List;

/// Portal 批量期刊卡片响应：存在且未软删的期刊，按请求顺序。
///
/// @param items 卡片，元素与期刊浏览列表项同形
/// @author linqibin
/// @since 0.1.0
public record PortalVenueBatchResponse(List<PortalVenueBrowseResponse> items) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public PortalVenueBatchResponse {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 创建响应。
  ///
  /// @param items 卡片
  /// @return 响应
  public static PortalVenueBatchResponse of(List<PortalVenueBrowseResponse> items) {
    return new PortalVenueBatchResponse(items);
  }
}
