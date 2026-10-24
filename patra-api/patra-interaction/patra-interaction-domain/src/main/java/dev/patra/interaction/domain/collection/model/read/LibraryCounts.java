package dev.patra.interaction.domain.collection.model.read;

/// 文献库「全部」「未分类」两个计数。
///
/// @param total 全部
/// @param unfiled 不在任何收藏夹里的
public record LibraryCounts(int total, int unfiled) {

  /// 创建。
  ///
  /// @param total 全部
  /// @param unfiled 未分类
  /// @return 计数
  public static LibraryCounts of(int total, int unfiled) {
    return new LibraryCounts(total, unfiled);
  }
}
