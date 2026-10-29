package dev.patra.interaction.app.reaction.usecase.helpful;

/// 点或取消之后的状态，供门户的乐观更新对账。
///
/// @param helpful 我现在是否点着
/// @param helpfulCount 目标现在的有帮助数
public record HelpfulResult(boolean helpful, int helpfulCount) {

  /// 创建。
  ///
  /// @param helpful 是否点着
  /// @param helpfulCount 有帮助数
  /// @return 结果
  public static HelpfulResult of(boolean helpful, int helpfulCount) {
    return new HelpfulResult(helpful, helpfulCount);
  }
}
