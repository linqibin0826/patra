package dev.patra.interaction.domain.reaction.model.vo;

/// 删掉的一条有帮助当初计入了谁的获赞。
///
/// @param creditedUserId 受益人；匿名目标为 `null`
public record HelpfulMark(Long creditedUserId) {

  /// 创建。
  ///
  /// @param creditedUserId 受益人，可空
  /// @return 明细
  public static HelpfulMark of(Long creditedUserId) {
    return new HelpfulMark(creditedUserId);
  }
}
