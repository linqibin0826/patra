package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.domain.collection.model.read.CollectionSummary;
import java.time.Instant;
import lombok.Builder;

/// 我的一个收藏夹。
///
/// @param collectionId ID
/// @param name 名称
/// @param description 简介，可空
/// @param visibility `PRIVATE` / `PUBLIC`
/// @param itemCount 篇数
/// @param createdAt 创建时间
/// @param updatedAt 更新时间（名称、简介、可见性、条目进出）
@Builder
public record CollectionResponse(
    Long collectionId,
    String name,
    String description,
    String visibility,
    int itemCount,
    Instant createdAt,
    Instant updatedAt) {

  /// 从读模型转换。
  ///
  /// @param summary 收藏夹
  /// @return 响应
  public static CollectionResponse from(CollectionSummary summary) {
    return CollectionResponse.builder()
        .collectionId(summary.collectionId().value())
        .name(summary.name())
        .description(summary.description())
        .visibility(summary.visibility().name())
        .itemCount(summary.itemCount())
        .createdAt(summary.createdAt())
        .updatedAt(summary.updatedAt())
        .build();
  }
}
