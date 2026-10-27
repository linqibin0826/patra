package dev.patra.interaction.domain.subject.model.vo;

/// 本地对象的所有者：登记时写入，之后不变。讨论区据此给所有者标「作者」，提醒据此发「评论了你的投稿经验」，
/// 匿名时两者都显示「匿名作者」。
///
/// @param userId 所有者的用户 ID
/// @param anonymous 所有者发布时是否选了匿名
public record SubjectOwner(long userId, boolean anonymous) {

  /// 创建。
  ///
  /// @param userId 所有者的用户 ID
  /// @param anonymous 是否匿名
  /// @return 所有者
  public static SubjectOwner of(long userId, boolean anonymous) {
    return new SubjectOwner(userId, anonymous);
  }
}
