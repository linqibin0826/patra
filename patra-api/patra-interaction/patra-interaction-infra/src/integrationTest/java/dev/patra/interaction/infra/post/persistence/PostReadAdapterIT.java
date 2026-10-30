package dev.patra.interaction.infra.post.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.read.ExperienceStats;
import dev.patra.interaction.domain.post.model.read.HelpfulRankedPost;
import dev.patra.interaction.domain.post.model.read.PostStats;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import dev.patra.interaction.domain.post.port.read.PostReadPort;
import dev.patra.interaction.domain.subject.model.vo.RankCursor;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 读端：排序与游标（最新投稿、最有帮助）、删除与匿名的过滤、修改历史的顺序、汇总的分位数（与 Excel 的
/// PERCENTILE.INC 一致）、每条经验的有帮助数 / 回复数 / 「我点过」、期刊下的条数。
class PostReadAdapterIT extends PostPersistenceITSupport {

  private static final LocalDate SINCE = LocalDate.parse("2023-10-27");

  @Autowired private PostReadPort reads;

  /// 投稿日期倒序；同一投稿日按发布时间倒序。
  @Test
  void should_order_by_submission_then_publish_time() {
    PostAggregate older = publish(U, J1, false, fields("2026-03-02"), null, T0);
    PostAggregate sameDayLater =
        publish(V, J1, false, fields("2026-03-02"), null, T0.plusSeconds(60));
    PostAggregate newest = publish(U, J1, false, fields("2026-05-01"), null, T0);

    assertThat(ids(reads.pageByJournal(J1, null, 10)))
        .containsExactly(newest.postId(), sameDayLater.postId(), older.postId());
  }

  /// 每页 2 条翻三页，不重不漏。
  @Test
  void should_page_with_cursor() {
    for (int day = 1; day <= 5; day++) {
      publish(U, J1, false, fields("2026-03-0" + day), null, T0);
    }

    List<PostAggregate> first = reads.pageByJournal(J1, null, 2);
    List<PostAggregate> second = reads.pageByJournal(J1, first.getLast().postId(), 2);
    List<PostAggregate> third = reads.pageByJournal(J1, second.getLast().postId(), 2);

    assertThat(first)
        .extracting(p -> p.fields().submittedOn().getDayOfMonth())
        .containsExactly(5, 4);
    assertThat(second)
        .extracting(p -> p.fields().submittedOn().getDayOfMonth())
        .containsExactly(3, 2);
    assertThat(third).extracting(p -> p.fields().submittedOn().getDayOfMonth()).containsExactly(1);
  }

  /// 游标那条后来被删了，照样接着翻。
  @Test
  void should_page_after_deleted_cursor() {
    publish(U, J1, false, fields("2026-03-03"), null, T0);
    PostAggregate cursor = publish(U, J1, false, fields("2026-03-02"), null, T0);
    PostAggregate last = publish(U, J1, false, fields("2026-03-01"), null, T0);
    delete(cursor.postId(), T0.plusSeconds(60));

    assertThat(ids(reads.pageByJournal(J1, cursor.postId(), 10))).containsExactly(last.postId());
  }

  /// 已删除的、别的期刊的都不在期刊列表里。
  @Test
  void should_exclude_deleted_and_other_journals() {
    PostAggregate kept = publish(U, J1, false, fields("2026-03-02"), null, T0);
    PostAggregate deleted = publish(U, J1, false, fields("2026-03-03"), null, T0);
    publish(U, J2, false, fields("2026-03-04"), null, T0);
    delete(deleted.postId(), T0.plusSeconds(60));

    assertThat(ids(reads.pageByJournal(J1, null, 10))).containsExactly(kept.postId());
  }

  /// 作者列表：别人看只有署名的，本人看全部；都不含已删除的。
  @Test
  void should_filter_anonymous_for_author_list() {
    PostAggregate named = publish(U, J1, false, fields("2026-03-02"), null, T0);
    PostAggregate anonymous = publish(U, J2, true, fields("2026-03-03"), null, T0);
    PostAggregate deleted = publish(U, J2, false, fields("2026-03-04"), null, T0);
    publish(V, J1, false, fields("2026-03-05"), null, T0);
    delete(deleted.postId(), T0.plusSeconds(60));

    assertThat(ids(reads.pageByAuthor(U, false, null, 10))).containsExactly(named.postId());
    assertThat(ids(reads.pageByAuthor(U, true, null, 10)))
        .containsExactly(anonymous.postId(), named.postId());
    assertThat(ids(reads.pageByAuthor(U, true, anonymous.postId(), 10)))
        .containsExactly(named.postId());
  }

  /// 修改历史最新的在前。
  @Test
  void should_return_revisions_newest_first() {
    PostAggregate post = publish(U, J1, false, fields("2026-03-02"), "第一版", T0);
    PostRevision first = PostRevision.of(post.fields(), post.body(), T0, T0.plusSeconds(600));
    PostRevision second =
        PostRevision.of(fields("2026-03-03"), null, T0.plusSeconds(600), T0.plusSeconds(1200));
    transactions.executeWithoutResult(
        s -> {
          posts.insertRevision(post.postId(), first);
          posts.insertRevision(post.postId(), second);
        });

    assertThat(reads.revisions(post.postId())).containsExactly(second, first);
    assertThat(reads.revisions(PostId.of(1L))).isEmpty();
  }

  /// 非直接拒稿用时 10、20、30、40 天 → P25 17.5、中位 25、P75 32.5；直接拒稿 3、5 天 → 中位 4；结果逐项计数。
  @Test
  void should_compute_quantiles_like_excel() {
    revised(10, FinalOutcome.ACCEPTED);
    revised(20, FinalOutcome.REJECTED);
    revised(30, FinalOutcome.WITHDRAWN);
    revised(40, FinalOutcome.IN_PROGRESS);
    deskRejected(3);
    deskRejected(5);

    ExperienceStats stats = reads.summarize(J1, SINCE);

    assertThat(stats.windowCount()).isEqualTo(6);
    assertThat(stats.allCount()).isEqualTo(6);
    assertThat(stats.firstRoundCount()).isEqualTo(4);
    assertThat(stats.firstRoundP25()).isEqualTo(17.5);
    assertThat(stats.firstRoundMedian()).isEqualTo(25.0);
    assertThat(stats.firstRoundP75()).isEqualTo(32.5);
    assertThat(stats.deskRejectCount()).isEqualTo(2);
    assertThat(stats.deskRejectMedian()).isEqualTo(4.0);
    assertThat(stats.accepted()).isEqualTo(1);
    assertThat(stats.rejected()).isEqualTo(3);
    assertThat(stats.withdrawn()).isEqualTo(1);
    assertThat(stats.inProgress()).isEqualTo(1);
  }

  /// 窗口外的只进全部条数，不进分位数与分布；没有样本时分位数为 null。
  @Test
  void should_count_window_and_all_separately() {
    publish(U, J1, false, fields("2022-03-02"), null, T0);
    deskRejected(3);

    ExperienceStats stats = reads.summarize(J1, SINCE);

    assertThat(stats.allCount()).isEqualTo(2);
    assertThat(stats.windowCount()).isEqualTo(1);
    assertThat(stats.firstRoundCount()).isZero();
    assertThat(stats.firstRoundMedian()).isNull();
    assertThat(stats.inProgress()).isZero();
    assertThat(reads.summarize(J2, SINCE).allCount()).isZero();
  }

  /// 首轮大修、用时 `days` 天、给定最终结果的一条（每条换一位作者）。
  ///
  /// @param days 首轮用时
  /// @param outcome 最终结果
  private void revised(int days, FinalOutcome outcome) {
    ExperienceFields f =
        fields("2026-01-01").toBuilder()
            .firstDecisionOn(LocalDate.parse("2026-01-01").plusDays(days))
            .finalOutcome(outcome)
            .build();
    publish(days, J1, false, f, null, T0);
  }

  /// 直接拒稿、用时 `days` 天的一条。
  ///
  /// @param days 用时
  private void deskRejected(int days) {
    ExperienceFields f =
        fields("2026-02-01").toBuilder()
            .firstDecisionOn(LocalDate.parse("2026-02-01").plusDays(days))
            .firstDecision(FirstDecision.DESK_REJECT)
            .finalOutcome(FinalOutcome.REJECTED)
            .build();
    publish(100 + days, J1, false, f, null, T0);
  }

  /// 有帮助数与「我点过」按 reaction 的表；回复数经登记表两跳到讨论的对象统计；没有统计的都是 0。
  @Test
  void should_find_stats_with_two_hop_reply_count() {
    PostAggregate p1 = publish(U, J1, false, fields("2026-03-01"), null, T0);
    PostAggregate p2 = publish(V, J1, false, fields("2026-03-02"), null, T0);
    helpful(p1, 2);
    registered(31L, p1);
    jdbcTemplate.update("INSERT INTO discussion.subject_stats VALUES (31, 4)");
    jdbcTemplate.update(
        "INSERT INTO reaction.helpful (user_id, target_type, target_id, credited_user_id,"
            + " created_at) VALUES (7, 'POST', ?, ?, now())",
        p1.postId().value(),
        U);

    assertThat(reads.findStats(List.of(p1.postId(), p2.postId()), 7L))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(p1.postId(), PostStats.of(2, 4, true), p2.postId(), PostStats.of(0, 0, false)));
    assertThat(reads.findStats(List.of(p1.postId()), null))
        .containsExactlyEntriesOf(Map.of(p1.postId(), PostStats.of(2, 4, null)));
    assertThat(reads.findStats(List.of(), 7L)).isEmpty();
  }

  /// 「最有帮助」：有帮助数降序，相同时发布时间新的在前；三元组游标逐页翻完不重复；已删除与别的期刊的不出现。
  @Test
  void should_page_journal_by_helpful_then_newest() {
    PostAggregate oldOne = publish(U, J1, false, fields("2026-05-01"), null, T0);
    PostAggregate newOne = publish(U, J1, false, fields("2026-03-01"), null, T0.plusSeconds(60));
    PostAggregate top = publish(V, J1, false, fields("2026-01-01"), null, T0);
    PostAggregate gone = publish(V, J1, false, fields("2026-02-01"), null, T0);
    PostAggregate elsewhere = publish(V, J2, false, fields("2026-02-01"), null, T0);
    helpful(oldOne, 1);
    helpful(newOne, 1);
    helpful(top, 3);
    helpful(gone, 9);
    helpful(elsewhere, 9);
    delete(gone.postId(), T0.plusSeconds(120));

    List<HelpfulRankedPost> all = reads.pageByJournalHelpful(J1, null, 10);
    assertThat(all.stream().map(r -> r.post().postId()))
        .containsExactly(top.postId(), newOne.postId(), oldOne.postId());
    assertThat(all).extracting(HelpfulRankedPost::helpfulCount).containsExactly(3, 1, 1);
    List<PostId> paged = new ArrayList<>();
    RankCursor after = null;
    for (int page = 0; page < 5; page++) {
      List<HelpfulRankedPost> one = reads.pageByJournalHelpful(J1, after, 1);
      if (one.isEmpty()) {
        break;
      }
      PostAggregate last = one.getLast().post();
      paged.add(last.postId());
      after = RankCursor.of(one.getLast().helpfulCount(), last.createdAt(), last.postId().value());
    }
    assertThat(paged).containsExactly(top.postId(), newOne.postId(), oldOne.postId());
  }

  /// 期刊下未删除的经验数。
  @Test
  void should_count_active_experiences_of_journal() {
    publish(U, J1, false, fields("2026-03-01"), null, T0);
    PostAggregate gone = publish(V, J1, false, fields("2026-03-02"), null, T0);
    publish(V, J2, false, fields("2026-03-02"), null, T0);
    delete(gone.postId(), T0.plusSeconds(1));

    assertThat(reads.countByJournal(J1)).isEqualTo(1);
    assertThat(reads.countByJournal(SubjectId.of(99L))).isZero();
  }

  /// 直接写一条经验的有帮助数。
  ///
  /// @param post 经验
  /// @param count 有帮助数
  private void helpful(PostAggregate post, int count) {
    jdbcTemplate.update(
        "INSERT INTO reaction.target_stats (target_type, target_id, helpful_count)"
            + " VALUES ('POST', ?, ?)",
        post.postId().value(),
        count);
  }

  /// 把经验登记成底座里的对象。
  ///
  /// @param subjectId 对象的本地 ID
  /// @param post 经验
  private void registered(long subjectId, PostAggregate post) {
    long id = post.postId().value();
    jdbcTemplate.update(
        "INSERT INTO subject.registry (subject_id, target_type, natural_key, source_id, state,"
            + " snapshot, snapshot_at, owner_user_id, owner_anonymous)"
            + " VALUES (?, 3, ?, ?, 'ACTIVE', '{}', now(), ?, false)",
        subjectId,
        "post:" + id,
        id,
        post.authorId());
  }

  /// 取 ID。
  ///
  /// @param page 经验
  /// @return ID
  private static List<PostId> ids(List<PostAggregate> page) {
    return page.stream().map(PostAggregate::postId).toList();
  }
}
