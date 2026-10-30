package dev.patra.interaction.domain.post.model.read;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;

/// 「最有帮助」列表里的一条：经验与排序时读出的有帮助数（下一页游标用它，与排序同一条查询）。
///
/// @param post 经验
/// @param helpfulCount 排序时的有帮助数
public record HelpfulRankedPost(PostAggregate post, int helpfulCount) {

  /// 创建。
  ///
  /// @param post 经验
  /// @param helpfulCount 有帮助数
  /// @return 条目
  public static HelpfulRankedPost of(PostAggregate post, int helpfulCount) {
    return new HelpfulRankedPost(post, helpfulCount);
  }
}
