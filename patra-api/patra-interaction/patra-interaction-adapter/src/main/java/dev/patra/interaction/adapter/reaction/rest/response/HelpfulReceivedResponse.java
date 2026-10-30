package dev.patra.interaction.adapter.reaction.rest.response;

/// 主页的获赞。原始类型 `long`，JSON 里是数字。
///
/// @param helpfulReceived 本人未删除、非匿名的评论与经验收到的有帮助总数
public record HelpfulReceivedResponse(long helpfulReceived) {

  /// 创建。
  ///
  /// @param helpfulReceived 获赞
  /// @return 响应
  public static HelpfulReceivedResponse of(long helpfulReceived) {
    return new HelpfulReceivedResponse(helpfulReceived);
  }
}
