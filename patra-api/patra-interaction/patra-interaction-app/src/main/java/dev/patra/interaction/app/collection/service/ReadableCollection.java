package dev.patra.interaction.app.collection.service;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.subject.model.read.UserCard;

/// 一个对当前访客可读的收藏夹，带所有者摘要。
///
/// @param collection 收藏夹
/// @param owner 所有者摘要；本人看自己的收藏夹而 identity 不可用或省略他时为 `null`
/// @param viewerIsOwner 访客是不是所有者本人
public record ReadableCollection(
    CollectionSummary collection, UserCard owner, boolean viewerIsOwner) {

  /// 创建。
  ///
  /// @param collection 收藏夹
  /// @param owner 所有者
  /// @param viewerIsOwner 是否本人
  /// @return 可读收藏夹
  public static ReadableCollection of(
      CollectionSummary collection, UserCard owner, boolean viewerIsOwner) {
    return new ReadableCollection(collection, owner, viewerIsOwner);
  }
}
