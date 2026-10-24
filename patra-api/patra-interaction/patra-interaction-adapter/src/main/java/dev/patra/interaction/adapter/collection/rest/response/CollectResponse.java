package dev.patra.interaction.adapter.collection.rest.response;

/// 收藏的结果。
///
/// @param created 是否新收进；已在库里为 `false`
/// @param item 条目
public record CollectResponse(boolean created, LibraryItemResponse item) {

  /// 创建。
  ///
  /// @param created 是否新收进
  /// @param item 条目
  /// @return 响应
  public static CollectResponse of(boolean created, LibraryItemResponse item) {
    return new CollectResponse(created, item);
  }
}
