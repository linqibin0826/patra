package dev.patra.interaction.domain.post.port.read;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.read.HelpfulRankedPost;
import dev.patra.interaction.domain.post.model.read.PostStats;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.subject.model.vo.RankCursor;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// 投稿经验的读端：期刊与作者的分页列表、修改历史、期刊汇总的原始统计、每条经验的公开计数。只读未删除的经验
/// （历史与计数除外）。公开计数按 SQL 读 reaction 与 discussion 的统计表，不依赖它们的 Java 类型。
///
/// 列表按投稿日期倒序、同日按发布时间倒序（再按 ID 倒序定序）；游标是上一页最后一条的 ID，帖子不物理删除，
/// 游标指向的经验被删了也能反查它的排序键。
public interface PostReadPort {

  /// 期刊下的经验一页。
  ///
  /// @param journal 期刊的本地 ID
  /// @param after 游标，可空（第一页）
  /// @param limit 条数
  /// @return 经验
  List<PostAggregate> pageByJournal(SubjectId journal, PostId after, int limit);

  /// 期刊下「最有帮助」的一页：有帮助数降序，相同时按 `(created_at, post_id)` 降序。
  ///
  /// @param journal 期刊的本地 ID
  /// @param after 游标，可空（第一页）
  /// @param limit 条数
  /// @return 经验与排序时读出的有帮助数
  List<HelpfulRankedPost> pageByJournalHelpful(SubjectId journal, RankCursor after, int limit);

  /// 期刊下未删除的经验数。
  ///
  /// @param journal 期刊的本地 ID
  /// @return 条数
  long countByJournal(SubjectId journal);

  /// 每条经验的有帮助数、回复数与访客点过没有。
  ///
  /// @param postIds 经验
  /// @param viewerId 访客，匿名为 `null`
  /// @return 经验 → 统计；每个传入的 ID 都在结果里（没有统计为 0）
  Map<PostId, PostStats> findStats(Collection<PostId> postIds, Long viewerId);

  /// 作者的经验一页。
  ///
  /// @param authorId 作者
  /// @param includeAnonymous 是否包含匿名发布的（只有本人看自己时为真）
  /// @param after 游标，可空
  /// @param limit 条数
  /// @return 经验
  List<PostAggregate> pageByAuthor(
      long authorId, boolean includeAnonymous, PostId after, int limit);

  /// 被替换掉的旧版本，最新的在前。
  ///
  /// @param id 帖子 ID
  /// @return 旧版本
  List<PostRevision> revisions(PostId id);

  /// 期刊汇总的原始统计（读取时由 PG 计算，分位数用 `percentile_cont`）。
  ///
  /// @param journal 期刊的本地 ID
  /// @param since 窗口起点（投稿日期，含）
  /// @return 统计
  ExperienceStats summarize(SubjectId journal, LocalDate since);
}
