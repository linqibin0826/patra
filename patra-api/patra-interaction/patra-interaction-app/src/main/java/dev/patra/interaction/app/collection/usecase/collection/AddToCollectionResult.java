package dev.patra.interaction.app.collection.usecase.collection;

/// 放进收藏夹的结果（简报「批量部分超限回篇数」）。
///
/// @param added 放进了几篇
/// @param alreadyIn 原本就在夹里的
/// @param skippedFull 夹满 5,000 篇后没放进的
/// @param skippedNotInLibrary 不在文献库里、没放进的
public record AddToCollectionResult(
    int added, int alreadyIn, int skippedFull, int skippedNotInLibrary) {

  /// 创建放进收藏夹的结果。
  ///
  /// @param added 放进了几篇
  /// @param alreadyIn 原本就在夹里的
  /// @param skippedFull 夹满 5,000 篇后没放进的
  /// @param skippedNotInLibrary 不在文献库里、没放进的
  /// @return 结果
  public static AddToCollectionResult of(
      int added, int alreadyIn, int skippedFull, int skippedNotInLibrary) {
    return new AddToCollectionResult(added, alreadyIn, skippedFull, skippedNotInLibrary);
  }
}
