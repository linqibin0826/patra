package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.LibraryItemView;
import dev.patra.interaction.domain.collection.model.vo.CollectionId;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// 我的文献库里的一条。ID 在 JSON 里是字符串。
///
/// @param itemId 条目 ID（已有条目的一切操作都按它）
/// @param type 对象类型（`publication`）
/// @param key catalog 文献 ID；catalog 整行消失时为 `null`
/// @param state `ACTIVE` / `UNAVAILABLE`
/// @param title 已失效时收藏时记下的标题，正常时为 `null`
/// @param note 备注
/// @param collectedAt 收藏时间
/// @param addedAt 加入当前收藏夹的时间，只在收藏夹视图里有
/// @param collectionIds 所在的收藏夹
@Builder
public record LibraryItemResponse(
    Long itemId,
    String type,
    String key,
    String state,
    String title,
    String note,
    Instant collectedAt,
    Instant addedAt,
    List<Long> collectionIds) {

  /// 防御性拷贝。
  public LibraryItemResponse {
    collectionIds = collectionIds != null ? List.copyOf(collectionIds) : List.of();
  }

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static LibraryItemResponse from(LibraryItemView view) {
    return LibraryItemResponse.builder()
        .itemId(view.itemId().value())
        .type(view.type().slug())
        .key(view.key())
        .state(view.state().name())
        .title(view.title())
        .note(view.note())
        .collectedAt(view.collectedAt())
        .addedAt(view.addedAt())
        .collectionIds(view.collectionIds().stream().map(CollectionId::value).toList())
        .build();
  }
}
