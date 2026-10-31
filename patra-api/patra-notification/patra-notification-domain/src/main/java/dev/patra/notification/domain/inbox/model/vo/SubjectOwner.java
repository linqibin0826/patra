package dev.patra.notification.domain.inbox.model.vo;

/// 被点内容所在对象的所有者（来自事件的 `subject`，同一条内容恒定）：判断点的人要不要显示成「匿名作者」。
///
/// @param ownerId 投稿经验的作者；文献为空
/// @param anonymous 投稿经验是匿名发布的
public record SubjectOwner(Long ownerId, boolean anonymous) {

  /// 构造。
  ///
  /// @param ownerId 所有者，可空
  /// @param anonymous 是否匿名
  /// @return 所有者
  public static SubjectOwner of(Long ownerId, boolean anonymous) {
    return new SubjectOwner(ownerId, anonymous);
  }

  /// 这个人要不要藏起来：他是匿名经验的作者本人（给自己经验下别人的评论点了有帮助），写出他的名字就等于告诉对方这篇
  /// 匿名经验是谁写的（PAP-144 spec 6.3，与回复提醒的「匿名作者」判定一致）。
  ///
  /// @param actorId 点的人
  /// @return 要显示成「匿名作者」为 `true`
  public boolean hides(long actorId) {
    return anonymous && ownerId != null && ownerId == actorId;
  }
}
