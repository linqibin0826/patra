package dev.patra.interaction.app.collection.usecase.query;

import java.util.List;

/// 一个范围里可导出的 key，顺序与同一排序下的列表一致。
///
/// @param keys catalog 文献 ID
/// @param skippedUnavailable 因已失效跳过的条数
public record ExportKeysView(List<String> keys, int skippedUnavailable) {

  /// 防御性拷贝。
  public ExportKeysView {
    keys = List.copyOf(keys);
  }

  /// 创建。
  ///
  /// @param keys key
  /// @param skippedUnavailable 跳过条数
  /// @return 结果
  public static ExportKeysView of(List<String> keys, int skippedUnavailable) {
    return new ExportKeysView(keys, skippedUnavailable);
  }
}
