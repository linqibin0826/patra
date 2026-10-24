package dev.patra.interaction.adapter.collection.rest.request;

import java.util.List;
import java.util.Objects;

/// 批量「我的状态」的请求体。拷贝时 `null` 或含 `null` 的列表变成空列表，由查询按「个数越界」拒绝（422）。
///
/// @param type 对外类型名（`publication`）
/// @param keys 对外 key，1 ~ 100 个
public record StatusRequest(String type, List<String> keys) {

  /// 防御性拷贝。
  public StatusRequest {
    keys = keys == null || keys.stream().anyMatch(Objects::isNull) ? List.of() : List.copyOf(keys);
  }
}
