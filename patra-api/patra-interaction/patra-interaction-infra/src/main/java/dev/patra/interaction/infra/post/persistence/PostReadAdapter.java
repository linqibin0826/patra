package dev.patra.interaction.infra.post.persistence;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/// `PostReadPort` 的原生 SQL 实现。列表用键集分页：游标反查自己的排序键 `(submitted_on, created_at, post_id)`，
/// 有无游标是两条 SQL（PG 推不出 `:after IS NULL` 里空参数的类型）。汇总一条 SQL 在 PG 里算完（spec 第 7 节），
/// 分位数原值交给领域规则取整。
@Component
@RequiredArgsConstructor
public class PostReadAdapter implements PostReadPort {

  /// 列表排序。
  private static final String ORDER =
      " ORDER BY e.submitted_on DESC, p.created_at DESC, p.post_id DESC LIMIT :limit";

  /// 排在游标之后：与游标那条的排序键比较（它被删了也查得到）。
  private static final String AFTER_CURSOR =
      " AND (e.submitted_on, p.created_at, p.post_id) < (SELECT ce.submitted_on, cp.created_at,"
          + " cp.post_id FROM post.post cp JOIN post.submission_experience ce"
          + " ON ce.post_id = cp.post_id WHERE cp.post_id = :after)";

  /// 期刊汇总：窗口内按投稿日期筛，分位数按非直接拒稿 / 直接拒稿分组，结果按最终结果计数。
  private static final String SUMMARY =
      """
      SELECT count(*) AS all_count,
             count(*) FILTER (WHERE e.submitted_on >= :since) AS window_count,
             count(*) FILTER (WHERE e.submitted_on >= :since AND e.first_decision <> 'DESK_REJECT')
               AS first_round_n,
             percentile_cont(ARRAY[0.25, 0.5, 0.75]::float8[])
               WITHIN GROUP (ORDER BY e.first_decision_on - e.submitted_on)
               FILTER (WHERE e.submitted_on >= :since AND e.first_decision <> 'DESK_REJECT')
               AS first_round_q,
             count(*) FILTER (WHERE e.submitted_on >= :since AND e.first_decision = 'DESK_REJECT')
               AS desk_n,
             percentile_cont(0.5)
               WITHIN GROUP (ORDER BY e.first_decision_on - e.submitted_on)
               FILTER (WHERE e.submitted_on >= :since AND e.first_decision = 'DESK_REJECT')
               AS desk_median,
             count(*) FILTER (WHERE e.submitted_on >= :since AND e.final_outcome = 'ACCEPTED')
               AS accepted,
             count(*) FILTER (WHERE e.submitted_on >= :since AND e.final_outcome = 'REJECTED')
               AS rejected,
             count(*) FILTER (WHERE e.submitted_on >= :since AND e.final_outcome = 'WITHDRAWN')
               AS withdrawn,
             count(*) FILTER (WHERE e.submitted_on >= :since AND e.final_outcome = 'IN_PROGRESS')
               AS in_progress
        FROM post.post p JOIN post.submission_experience e ON e.post_id = p.post_id
       WHERE p.subject_id = :journal AND p.status = 'ACTIVE'
         AND p.post_type = 'SUBMISSION_EXPERIENCE'
      """;

  private final JdbcClient jdbc;
  private final PostRows rows;

  /// 期刊下的一页。
  ///
  /// @param journal 期刊
  /// @param after 游标，可空
  /// @param limit 条数
  /// @return 经验
  @Override
  public List<PostAggregate> pageByJournal(SubjectId journal, PostId after, int limit) {
    JdbcClient.StatementSpec spec =
        jdbc.sql(
                "SELECT "
                    + PostRows.COLUMNS
                    + PostRows.FROM
                    + " WHERE p.subject_id = :journal AND p.status = 'ACTIVE'"
                    + (after == null ? "" : AFTER_CURSOR)
                    + ORDER)
            .param("journal", journal.value())
            .param("limit", limit);
    return withCursor(spec, after).query(rows.postMapper()).list();
  }

  /// 作者的一页。
  ///
  /// @param authorId 作者
  /// @param includeAnonymous 是否包含匿名的
  /// @param after 游标，可空
  /// @param limit 条数
  /// @return 经验
  @Override
  public List<PostAggregate> pageByAuthor(
      long authorId, boolean includeAnonymous, PostId after, int limit) {
    JdbcClient.StatementSpec spec =
        jdbc.sql(
                "SELECT "
                    + PostRows.COLUMNS
                    + PostRows.FROM
                    + " WHERE p.author_user_id = :author AND p.status = 'ACTIVE'"
                    + (includeAnonymous ? "" : " AND NOT p.anonymous")
                    + (after == null ? "" : AFTER_CURSOR)
                    + ORDER)
            .param("author", authorId)
            .param("limit", limit);
    return withCursor(spec, after).query(rows.postMapper()).list();
  }

  /// 修改历史。
  ///
  /// @param id 帖子 ID
  /// @return 旧版本，最新的在前
  @Override
  public List<PostRevision> revisions(PostId id) {
    return jdbc.sql(
            "SELECT body, fields::text AS fields, written_at, replaced_at FROM post.revision"
                + " WHERE post_id = :id ORDER BY revision_no DESC")
        .param("id", id.value())
        .query(rows.revisionMapper())
        .list();
  }

  /// 期刊汇总的原始统计。
  ///
  /// @param journal 期刊
  /// @param since 窗口起点
  /// @return 统计
  @Override
  public ExperienceStats summarize(SubjectId journal, LocalDate since) {
    return jdbc.sql(SUMMARY)
        .param("journal", journal.value())
        .param("since", since)
        .query((rs, rowNum) -> stats(rs))
        .single();
  }

  /// 有游标时补上参数。
  ///
  /// @param spec 语句
  /// @param after 游标，可空
  /// @return 语句
  private static JdbcClient.StatementSpec withCursor(JdbcClient.StatementSpec spec, PostId after) {
    return after == null ? spec : spec.param("after", after.value());
  }

  /// 汇总行 → 统计。
  ///
  /// @param rs 结果集（当前行）
  /// @return 统计
  /// @throws SQLException 读列失败
  private static ExperienceStats stats(ResultSet rs) throws SQLException {
    Double[] quartiles = quartiles(rs.getArray("first_round_q"));
    return ExperienceStats.builder()
        .allCount(rs.getLong("all_count"))
        .windowCount(rs.getLong("window_count"))
        .firstRoundCount(rs.getLong("first_round_n"))
        .firstRoundP25(quartiles[0])
        .firstRoundMedian(quartiles[1])
        .firstRoundP75(quartiles[2])
        .deskRejectCount(rs.getLong("desk_n"))
        .deskRejectMedian(rs.getObject("desk_median", Double.class))
        .accepted(rs.getLong("accepted"))
        .rejected(rs.getLong("rejected"))
        .withdrawn(rs.getLong("withdrawn"))
        .inProgress(rs.getLong("in_progress"))
        .build();
  }

  /// `percentile_cont` 数组 → P25、中位数、P75；没有样本时三个都是 `null`。
  ///
  /// @param array 数组列，可空
  /// @return 三个值
  /// @throws SQLException 读数组失败
  private static Double[] quartiles(Array array) throws SQLException {
    return array == null ? new Double[3] : (Double[]) array.getArray();
  }
}
