package dev.patra.interaction.api.subject.event;

/// 集成事件里的对象：讨论所在的对象，或被点内容所在的对象（PAP-141）。
///
/// 只放算收件人与对上墓碑要用的事实，不放展示信息：文献的 key 在 catalog 重导后会变、文献会下线，key、标题、期刊
/// 由消费方按 `subjectId` 调 interaction 的内部接口读时现查。
///
/// @param subjectId 登记表本地 ID，永不变
/// @param type 对象种类：`PUBLICATION`（文献）/ `POST`（投稿经验）
/// @param ownerId 投稿经验的作者；文献为 `null`
/// @param ownerAnonymous 投稿经验是匿名发布的；文献为 `false`
public record SubjectContext(long subjectId, String type, Long ownerId, boolean ownerAnonymous) {

  /// 创建。
  ///
  /// @param subjectId 本地 ID
  /// @param type 种类
  /// @param ownerId 所有者，可空
  /// @param ownerAnonymous 是否匿名
  /// @return 对象
  public static SubjectContext of(
      long subjectId, String type, Long ownerId, boolean ownerAnonymous) {
    return new SubjectContext(subjectId, type, ownerId, ownerAnonymous);
  }
}
