package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.query.ExportKeysView;
import java.util.List;

/// 一个范围里可导出的 catalog 文献 ID，顺序与同一排序下的列表一致；门户拿去调 catalog 的导出与复制引用。
///
/// @param keys catalog 文献 ID
/// @param skippedUnavailable 因已失效跳过的条数
public record ExportKeysResponse(List<String> keys, int skippedUnavailable) {

  /// 防御性拷贝。
  public ExportKeysResponse {
    keys = List.copyOf(keys);
  }

  /// 从查询结果转换。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static ExportKeysResponse from(ExportKeysView view) {
    return new ExportKeysResponse(view.keys(), view.skippedUnavailable());
  }
}
