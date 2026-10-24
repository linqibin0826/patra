package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import java.time.Instant;
import lombok.Builder;

/// 主页「收藏夹」栏里的一张卡片。
///
/// @param collectionId ID
/// @param name 名称
/// @param description 简介，可空
/// @param itemCount 篇数
/// @param updatedAt 更新时间
@Builder
public record PublicCollectionCardResponse(
    Long collectionId, String name, String description, int itemCount, Instant updatedAt) {

  /// 从读模型转换。
  ///
  /// @param summary 收藏夹
  /// @return 响应
  public static PublicCollectionCardResponse from(CollectionSummary summary) {
    return PublicCollectionCardResponse.builder()
        .collectionId(summary.collectionId().value())
        .name(summary.name())
        .description(summary.description())
        .itemCount(summary.itemCount())
        .updatedAt(summary.updatedAt())
        .build();
  }
}
