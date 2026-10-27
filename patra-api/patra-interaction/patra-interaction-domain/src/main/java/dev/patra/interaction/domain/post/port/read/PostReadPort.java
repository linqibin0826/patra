package dev.patra.interaction.domain.post.port.read;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.LocalDate;
import java.util.List;

/// 投稿经验的读端：期刊与作者的分页列表、修改历史、期刊汇总的原始统计。只读未删除的经验（历史除外）。
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
