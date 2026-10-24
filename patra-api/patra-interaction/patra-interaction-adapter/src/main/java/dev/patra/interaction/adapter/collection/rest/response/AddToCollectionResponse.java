package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.collection.AddToCollectionResult;

/// 放进收藏夹的结果。
///
/// @param added 放进了几篇
/// @param alreadyIn 原本就在夹里的
/// @param skippedFull 夹满 5,000 篇后没放进的
/// @param skippedNotInLibrary 不在文献库里、没放进的
public record AddToCollectionResponse(
    int added, int alreadyIn, int skippedFull, int skippedNotInLibrary) {

  /// 从用例结果转换。
  ///
  /// @param result 结果
  /// @return 响应
  public static AddToCollectionResponse from(AddToCollectionResult result) {
    return new AddToCollectionResponse(
        result.added(), result.alreadyIn(), result.skippedFull(), result.skippedNotInLibrary());
  }
}
