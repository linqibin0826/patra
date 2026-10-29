package dev.patra.interaction.infra.post.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.interaction.domain.post.model.aggregate.PostAggregate;
import dev.patra.interaction.domain.post.model.enums.ArticleType;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.enums.PostStatus;
import dev.patra.interaction.domain.post.model.vo.ExperienceDraft;
import dev.patra.interaction.domain.post.model.vo.ExperienceFields;
import dev.patra.interaction.domain.post.model.vo.PostBody;
import dev.patra.interaction.domain.post.model.vo.PostRevision;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

/// 帖子写端：两张表往返、查重只看未删除、编辑只改内容、修改历史编号、软删、作者锁。
class PostRepositoryAdapterIT extends PostPersistenceITSupport {

  @Autowired private DataSource dataSource;

  /// 作者锁在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @DisplayName("作者锁必须在事务里调用")
  void should_reject_lock_outside_transaction() {
    assertThatThrownBy(() -> posts.lockAuthor(U))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 全部字段写进去读回来一样；选填全空、没有正文也一样。
  @Test
  void should_round_trip_post_with_all_fields() {
    ExperienceFields full =
        ExperienceFields.builder()
            .submittedOn(LocalDate.parse("2026-03-02"))
            .firstDecisionOn(LocalDate.parse("2026-04-13"))
            .firstDecision(FirstDecision.MAJOR_REVISION)
            .finalOutcome(FinalOutcome.ACCEPTED)
            .finalDecisionOn(LocalDate.parse("2026-06-01"))
            .articleType(ArticleType.SYSTEMATIC_REVIEW)
            .reviewRounds(2)
            .reviewQuality(4)
            .editorCommunication(3)
            .apcPaid(true)
            .previouslyRejected(false)
            .build();
    PostAggregate rich = publish(U, J1, true, full, "**粗体**\n- 一", T0);
    PostAggregate minimal = publish(V, J2, false, fields("2026-05-01"), null, T0);

    assertThat(posts.find(rich.postId())).contains(rich);
    assertThat(posts.find(minimal.postId())).contains(minimal);
  }

  /// 查重只命中同一作者、同一期刊、同一投稿日期、未删除的。
  @Test
  void should_find_active_by_submission_only() {
    PostAggregate post = publish(U, J1, false, fields("2026-03-02"), null, T0);

    assertThat(posts.findActiveBySubmission(U, J1, LocalDate.parse("2026-03-02")))
        .contains(post.postId());
    assertThat(posts.findActiveBySubmission(U, J2, LocalDate.parse("2026-03-02"))).isEmpty();
    assertThat(posts.findActiveBySubmission(V, J1, LocalDate.parse("2026-03-02"))).isEmpty();
    assertThat(posts.findActiveBySubmission(U, J1, LocalDate.parse("2026-03-03"))).isEmpty();

    delete(post.postId(), T0.plusSeconds(60));
    assertThat(posts.findActiveBySubmission(U, J1, LocalDate.parse("2026-03-02"))).isEmpty();
  }

  /// 编辑写回正文、字段与 `revised_at`，不动作者、期刊、匿名、发布时刻。
  @Test
  void should_update_content_and_revised_at_without_touching_identity() {
    PostAggregate post = publish(U, J1, true, fields("2026-03-02"), "第一版", T0);
    Instant now = T0.plus(Duration.ofMinutes(10));
    PostAggregate after =
        post.edit(
                ExperienceDraft.of(
                    fields("2026-03-05").toBuilder().reviewQuality(5).build(), new PostBody("第二版")),
                now)
            .after();

    transactions.executeWithoutResult(
        s ->
            posts.update(
                after.toBuilder().authorId(V).anonymous(false).subjectId(J2).build(), now));

    assertThat(posts.find(post.postId())).contains(after);
  }

  /// 修改历史的编号依次是 1、2，字段按 jsonb 存、原样读回。
  @Test
  void should_append_revisions_with_increasing_numbers() {
    PostAggregate post = publish(U, J1, false, fields("2026-03-02"), "第一版", T0);
    PostRevision first =
        PostRevision.of(post.fields(), post.body(), T0, T0.plus(Duration.ofMinutes(10)));
    PostRevision second =
        PostRevision.of(
            fields("2026-03-05"), null, T0.plus(Duration.ofMinutes(10)), T0.plusSeconds(3600));

    transactions.executeWithoutResult(
        s -> {
          posts.insertRevision(post.postId(), first);
          posts.insertRevision(post.postId(), second);
        });

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT revision_no FROM post.revision WHERE post_id = ? ORDER BY revision_no",
                Integer.class,
                post.postId().value()))
        .containsExactly(1, 2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT fields->>'firstDecision' FROM post.revision WHERE revision_no = 1",
                String.class))
        .isEqualTo("MAJOR_REVISION");
  }

  /// 软删：状态与删除时刻，内容留着。
  @Test
  void should_mark_deleted() {
    PostAggregate post = publish(U, J1, false, fields("2026-03-02"), "正文", T0);

    delete(post.postId(), T0.plusSeconds(60));

    PostAggregate deleted = posts.find(post.postId()).orElseThrow();
    assertThat(deleted.status()).isEqualTo(PostStatus.DELETED);
    assertThat(deleted.body()).isEqualTo(new PostBody("正文"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM post.post WHERE post_id = ?",
                Boolean.class,
                post.postId().value()))
        .isTrue();
  }

  /// 不存在的 ID。
  @Test
  void should_return_empty_for_missing_post() {
    assertThat(posts.find(posts.nextId())).isEmpty();
  }

  /// 作者锁：别的连接持有同一把锁时，`lockAuthor` 一直等到它释放。
  @Test
  void should_block_on_author_lock_held_elsewhere() throws Exception {
    try (Connection holder = dataSource.getConnection()) {
      advisory(holder, "pg_advisory_lock");
      try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
        CompletableFuture<Void> locked =
            CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(s -> posts.lockAuthor(U)), pool);
        awaitBlockedOnLock();
        assertThat(locked).isNotDone();

        advisory(holder, "pg_advisory_unlock");
        locked.get();
      }
    }
  }

  /// 在给定连接上对 `post.author:{U}` 加锁或解锁。
  ///
  /// @param holder 连接
  /// @param function `pg_advisory_lock` / `pg_advisory_unlock`
  /// @throws Exception 执行失败
  private static void advisory(Connection holder, String function) throws Exception {
    try (PreparedStatement statement =
        holder.prepareStatement("SELECT " + function + "(hashtextextended(?, 0))")) {
      statement.setString(1, "post.author:" + U);
      statement.execute();
    }
  }

  /// 等到有会话在等 advisory lock。
  ///
  /// @throws InterruptedException 被中断
  private void awaitBlockedOnLock() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      List<Long> waiting =
          jdbcTemplate.queryForList(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                  + " AND wait_event = 'advisory'",
              Long.class);
      if (!waiting.isEmpty() && waiting.getFirst() > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("没有等到 advisory lock 上的等待");
  }
}
