package dev.patra.interaction.app.discussion.usecase.query;

import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import java.util.List;

/// 修改历史。
///
/// @param versions 版本，最新的（当前）在前，最后一个是最初版本（时刻是发表时刻）
/// @param mentions 全部版本里提及的人
public record RevisionsView(List<RevisionRecord> versions, List<MentionView> mentions) {

  /// 防御性拷贝。
  public RevisionsView {
    versions = List.copyOf(versions);
    mentions = List.copyOf(mentions);
  }

  /// 创建。
  ///
  /// @param versions 版本
  /// @param mentions 提及
  /// @return 修改历史
  public static RevisionsView of(List<RevisionRecord> versions, List<MentionView> mentions) {
    return new RevisionsView(versions, mentions);
  }
}
