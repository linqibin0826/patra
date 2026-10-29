package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import dev.patra.interaction.domain.subject.model.read.UserCard;

/// 公开收藏夹页的头部。
///
/// @param collection 收藏夹
/// @param owner 所有者摘要；本人看自己的收藏夹而 identity 不可用或省略他时为 `null`
/// @param viewerIsOwner 访客是不是所有者本人
public record PublicCollectionView(
    CollectionSummary collection, UserCard owner, boolean viewerIsOwner) {

  /// 创建。
  ///
  /// @param collection 收藏夹
  /// @param owner 所有者
  /// @param viewerIsOwner 是否本人
  /// @return 头部
  public static PublicCollectionView of(
      CollectionSummary collection, UserCard owner, boolean viewerIsOwner) {
    return new PublicCollectionView(collection, owner, viewerIsOwner);
  }
}
