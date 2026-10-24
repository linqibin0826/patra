package dev.patra.interaction.adapter.collection.rest.request;

import java.util.List;
import java.util.Objects;

/// 放进收藏夹的请求体。拷贝时 `null` 或含 `null` 的列表变成空列表，由用例按「个数越界」拒绝（422）。
///
/// @param itemIds 条目 ID，1 ~ 100 个（JSON 里字符串或数字都收）
public record ItemIdsRequest(List<Long> itemIds) {

  /// 防御性拷贝。
  public ItemIdsRequest {
    itemIds =
        itemIds == null || itemIds.stream().anyMatch(Objects::isNull)
            ? List.of()
            : List.copyOf(itemIds);
  }
}
