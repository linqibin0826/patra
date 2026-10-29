package dev.patra.catalog.adapter.portal.rest.response;

import java.util.List;

/// Portal 批量文献卡片响应：存在且未软删的文献，按请求顺序。
///
/// @param items 卡片，元素与 feed / 检索列表项同形
/// @author linqibin
/// @since 0.1.0
public record PortalPaperBatchResponse(List<PortalPaperResponse> items) {

  /// 紧凑构造器：防御性拷贝，`null` 视为空列表。
  public PortalPaperBatchResponse {
    items = items != null ? List.copyOf(items) : List.of();
  }

  /// 创建响应。
  ///
  /// @param items 卡片
  /// @return 响应
  public static PortalPaperBatchResponse of(List<PortalPaperResponse> items) {
    return new PortalPaperBatchResponse(items);
  }
}
