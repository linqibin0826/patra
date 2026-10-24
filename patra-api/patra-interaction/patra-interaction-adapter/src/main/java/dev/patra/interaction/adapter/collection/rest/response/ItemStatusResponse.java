package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.ItemStatusView;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
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
public record ItemStatusResponse(
    String key,
    boolean collected,
    Long itemId,
    Instant collectedAt,
    List<Long> collectionIds,
    boolean hasNote) {

  /// 防御性拷贝。
  public ItemStatusResponse {
    collectionIds = collectionIds != null ? List.copyOf(collectionIds) : List.of();
  }

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static ItemStatusResponse from(ItemStatusView view) {
    return ItemStatusResponse.builder()
        .key(view.key())
        .collected(view.collected())
        .itemId(view.itemId() == null ? null : view.itemId().value())
        .collectedAt(view.collectedAt())
        .collectionIds(view.collectionIds().stream().map(CollectionId::value).toList())
        .hasNote(view.hasNote())
        .build();
  }
}
