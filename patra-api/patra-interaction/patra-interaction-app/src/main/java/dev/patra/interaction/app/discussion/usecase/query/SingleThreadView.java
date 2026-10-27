package dev.patra.interaction.app.discussion.usecase.query;

/// 单楼视图：目标所在的整楼。
///
/// @param targetId 目标评论
/// @param thread 整楼，回复全部给出
public record SingleThreadView(long targetId, ThreadView thread) {

  /// 创建。
  ///
  /// @param targetId 目标
  /// @param thread 整楼
  /// @return 单楼
  public static SingleThreadView of(long targetId, ThreadView thread) {
    return new SingleThreadView(targetId, thread);
  }
}
