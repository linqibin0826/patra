package dev.patra.interaction.adapter.post.rest.response;

import java.util.List;

/// 修改历史：当前版本在前，旧版本随后（最新的在前）。
///
/// @param items 版本
public record RevisionsResponse(List<RevisionResponse> items) {

  /// 防御性拷贝。
  public RevisionsResponse {
    items = List.copyOf(items);
  }

  /// 创建。
  ///
  /// @param items 版本
  /// @return 响应
  public static RevisionsResponse of(List<RevisionResponse> items) {
    return new RevisionsResponse(items);
  }
}
