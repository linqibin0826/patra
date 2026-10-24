package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.PublicCollectionView;
import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import java.time.Instant;
import lombok.Builder;

/// 公开收藏夹页的头部。
///
/// @param collectionId ID
/// @param name 名称
/// @param description 简介，可空
/// @param visibility `PRIVATE`（只有所有者本人能读到）/ `PUBLIC`
/// @param itemCount 篇数
/// @param updatedAt 更新时间
/// @param owner 所有者；本人看自己的收藏夹而 identity 暂时不可用时为 `null`（门户降级显示）
/// @param viewerIsOwner 访客是不是所有者本人
@Builder
public record PublicCollectionResponse(
    Long collectionId,
    String name,
    String description,
    String visibility,
    int itemCount,
    Instant updatedAt,
    OwnerResponse owner,
    boolean viewerIsOwner) {

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static PublicCollectionResponse from(PublicCollectionView view) {
    CollectionSummary c = view.collection();
    return PublicCollectionResponse.builder()
        .collectionId(c.collectionId().value())
        .name(c.name())
        .description(c.description())
        .visibility(c.visibility().name())
        .itemCount(c.itemCount())
        .updatedAt(c.updatedAt())
        .owner(view.owner() == null ? null : OwnerResponse.from(view.owner()))
        .viewerIsOwner(view.viewerIsOwner())
        .build();
  }
}
