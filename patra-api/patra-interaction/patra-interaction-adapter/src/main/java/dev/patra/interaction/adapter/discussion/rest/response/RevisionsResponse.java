package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.RevisionsView;
import java.util.List;

/// 修改历史（公开）。
///
/// @param versions 最新（当前）在前，最后一个是最初版本
/// @param mentions 全部版本里提及的人
public record RevisionsResponse(List<RevisionResponse> versions, List<MentionResponse> mentions) {

  /// 防御性拷贝。
  public RevisionsResponse {
    versions = List.copyOf(versions);
    mentions = List.copyOf(mentions);
  }

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static RevisionsResponse from(RevisionsView view) {
    return new RevisionsResponse(
        view.versions().stream().map(RevisionResponse::from).toList(),
        view.mentions().stream().map(MentionResponse::from).toList());
  }
}
