package dev.patra.interaction.domain.collection.model.read;

import dev.patra.interaction.domain.collection.model.enums.CollectionVisibility;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import java.time.Instant;
import lombok.Builder;

/// 一个收藏夹的元数据与篇数。
///
/// @param collectionId ID
/// @param ownerId 所有者的用户 ID
/// @param name 名称
/// @param description 简介，没有为 `null`
/// @param visibility 可见性
/// @param itemCount 篇数
/// @param createdAt 创建时间
/// @param updatedAt 更新时间（名称、简介、可见性、条目进出）
@Builder
public record CollectionSummary(
    CollectionId collectionId,
    long ownerId,
    String name,
    String description,
    CollectionVisibility visibility,
    int itemCount,
    Instant createdAt,
    Instant updatedAt) {

  /// 是否公开。
  ///
  /// @return 公开时为 `true`
  public boolean isPublic() {
    return visibility == CollectionVisibility.PUBLIC;
  }
}
