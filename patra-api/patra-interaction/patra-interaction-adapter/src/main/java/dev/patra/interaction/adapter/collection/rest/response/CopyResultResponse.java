package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.app.collection.usecase.copy.CopyResult;
import lombok.Builder;

/// 复制收藏夹的结果。
///
/// @param collectionId 新收藏夹
/// @param name 实际名称（重名时加了序号）
/// @param addedToLibrary 新收进文献库的篇数
/// @param alreadyInLibrary 原本就在文献库里的篇数
/// @param skippedUnavailable 因已失效没有复制的篇数
@Builder
public record CopyResultResponse(
    Long collectionId,
    String name,
    int addedToLibrary,
    int alreadyInLibrary,
    int skippedUnavailable) {

  /// 从用例结果转换。
  ///
  /// @param result 结果
  /// @return 响应
  public static CopyResultResponse from(CopyResult result) {
    return CopyResultResponse.builder()
        .collectionId(result.collectionId().value())
        .name(result.name())
        .addedToLibrary(result.addedToLibrary())
        .alreadyInLibrary(result.alreadyInLibrary())
        .skippedUnavailable(result.skippedUnavailable())
        .build();
  }
}
