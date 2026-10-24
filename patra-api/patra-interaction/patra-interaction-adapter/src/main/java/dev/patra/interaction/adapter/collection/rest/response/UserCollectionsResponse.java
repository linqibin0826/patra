package dev.patra.interaction.adapter.collection.rest.response;

import java.util.List;

/// 一个用户对访客可见的公开收藏夹（按更新时间倒序）；不可见时为空列表。
///
/// @param collections 卡片
public record UserCollectionsResponse(List<PublicCollectionCardResponse> collections) {

  /// 防御性拷贝。
  public UserCollectionsResponse {
    collections = List.copyOf(collections);
  }

  /// 创建。
  ///
  /// @param collections 卡片
  /// @return 响应
  public static UserCollectionsResponse of(List<PublicCollectionCardResponse> collections) {
    return new UserCollectionsResponse(collections);
  }
}
