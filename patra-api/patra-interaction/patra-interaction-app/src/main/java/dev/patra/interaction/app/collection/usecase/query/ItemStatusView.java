package dev.patra.interaction.app.collection.usecase.query;

import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 「我的状态」里的一个 key。
///
/// @param key 请求里的 key
/// @param collected 是否已收藏
/// @param itemId 条目，未收藏为 `null`
/// @param collectedAt 收藏时间，未收藏为 `null`
/// @param collectionIds 所在的收藏夹
/// @param hasNote 是否有备注
@Builder
public record ItemStatusView(
    String key,
    boolean collected,
    SubjectId itemId,
    Instant collectedAt,
    List<CollectionId> collectionIds,
    boolean hasNote) {

  /// 防御性拷贝。
  public ItemStatusView {
    collectionIds = collectionIds != null ? List.copyOf(collectionIds) : List.of();
  }
}
