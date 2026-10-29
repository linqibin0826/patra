package dev.patra.interaction.adapter.reaction.rest.response;

import dev.patra.interaction.app.reaction.usecase.helpful.HelpfulResult;

/// 点或取消之后的状态，门户据此校正乐观更新。
///
/// @param helpful 我现在是否点着
/// @param helpfulCount 目标现在的有帮助数
public record HelpfulResponse(boolean helpful, int helpfulCount) {

  /// 从结果转换。
  ///
  /// @param result 结果
  /// @return 响应
  public static HelpfulResponse from(HelpfulResult result) {
    return new HelpfulResponse(result.helpful(), result.helpfulCount());
  }
}
