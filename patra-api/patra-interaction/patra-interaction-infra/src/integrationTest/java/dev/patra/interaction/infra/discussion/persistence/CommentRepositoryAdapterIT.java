package dev.patra.interaction.infra.discussion.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.read.StatsCorrection;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;

/// 评论的写端：插入根评论与回复、改正文、存修改历史、变占位、删除、楼锁、楼统计与对象统计。
class CommentRepositoryAdapterIT extends DiscussionPersistenceITSupport {

  @Autowired private CommentRepository comments;
  @Autowired private DataSource dataSource;

  /// 楼锁、删除锁、编辑锁在事务外调用时被拒绝（`MANDATORY`）。
  @Test
  @DisplayName("楼锁、删除锁、编辑锁都必须在事务里调用")
  void should_reject_lock_outside_transaction() {
    assertThatThrownBy(() -> comments.lockThread(1L))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> comments.findForUpdate(1L))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> comments.findForEdit(1L))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> comments.removeReply(1L))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 记两条回复：回复数 2，最后回复时间是较晚的那条。
  @Test
  void should_record_replies_and_keep_latest_time() {
    transactions.executeWithoutResult(
        s -> {
          comments.recordReply(7L, T0.plusSeconds(1));
          comments.recordReply(7L, T0.plusSeconds(2));
        });

    assertThat(threadStats(7L)).containsExactly(2, T0.plusSeconds(2));
  }

  /// 后拿到楼锁的回复时间可能更早，不能把最后回复时间往回拨。
  @Test
  void should_keep_latest_time_when_older_reply_recorded() {
    transactions.executeWithoutResult(
        s -> {
          comments.recordReply(7L, T0.plusSeconds(2));
          comments.recordReply(7L, T0.plusSeconds(1));
        });

    assertThat(threadStats(7L)).containsExactly(2, T0.plusSeconds(2));
  }

  /// 删掉最新的回复：回复数减一，最后回复时间回退到剩下的最新一条（占位不算）。
  @Test
  void should_fall_back_to_previous_reply_when_latest_removed() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    insert(root, root, 2L, "a", List.of(), T0.plusSeconds(1));
    long b = insert(root, root, 2L, "b", List.of(), T0.plusSeconds(2));
    long c = insert(root, b, 3L, "c", List.of(), T0.plusSeconds(3));
    transactions.executeWithoutResult(
        s -> {
          comments.recordReply(root, T0.plusSeconds(1));
          comments.recordReply(root, T0.plusSeconds(2));
          comments.recordReply(root, T0.plusSeconds(3));
        });

    transactions.executeWithoutResult(
        s -> {
          comments.markDeleted(b, T0.plusSeconds(9));
          comments.removeReply(root);
        });
    assertThat(threadStats(root)).containsExactly(2, T0.plusSeconds(3));

    transactions.executeWithoutResult(
        s -> {
          comments.delete(c);
          comments.removeReply(root);
        });
    assertThat(threadStats(root)).containsExactly(1, T0.plusSeconds(1));
  }

  /// 删掉楼里最后一条回复：回复数 0、最后回复时间为空，不撞约束。
  @Test
  void should_reset_to_zero_when_last_reply_removed() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    long reply = insert(root, root, 2L, "回复", List.of(), T0.plusSeconds(1));
    transactions.executeWithoutResult(s -> comments.recordReply(root, T0.plusSeconds(1)));

    transactions.executeWithoutResult(
        s -> {
          comments.delete(reply);
          comments.removeReply(root);
        });

    assertThat(threadStats(root)).containsExactly(0, null);
  }

  /// 根评论整条去掉时删它的楼统计行。
  @Test
  void should_drop_thread_stats() {
    transactions.executeWithoutResult(s -> comments.recordReply(7L, T0));

    transactions.executeWithoutResult(s -> comments.dropThreadStats(7L));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM discussion.thread_stats WHERE root_id = 7", Long.class))
        .isZero();
  }

  /// 对象统计按对象加减；没动过的对象没有行。
  @Test
  void should_count_comments_per_subject() {
    transactions.executeWithoutResult(
        s -> {
          comments.incrementCommentCount(S1);
          comments.incrementCommentCount(S1);
          comments.decrementCommentCount(S1);
        });

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT subject_id, comment_count FROM discussion.subject_stats"))
        .containsExactly(Map.of("subject_id", S1.value(), "comment_count", 1));
  }

  /// 根评论的根是自己，回复的根是所在楼的根；字段原样读回。
  @Test
  void should_insert_root_and_reply() {
    long root = insert(null, null, 1L, "根", List.of(7L), T0);
    long reply = insert(root, root, 2L, "回复", List.of(), T0.plusSeconds(1));

    CommentAggregate saved = comments.find(root).orElseThrow();
    assertThat(saved.getRootId()).isEqualTo(root);
    assertThat(saved.getParentId()).isNull();
    assertThat(saved.getSubjectId()).isEqualTo(S1);
    assertThat(saved.getAuthorId()).isEqualTo(1L);
    assertThat(saved.getState()).isEqualTo(CommentState.ACTIVE);
    assertThat(saved.getBody()).isEqualTo("根");
    assertThat(saved.getMentionedUserIds()).containsExactly(7L);
    assertThat(saved.getCreatedAt()).isEqualTo(T0);
    assertThat(saved.getRevisedAt()).isNull();
    CommentAggregate savedReply = comments.find(reply).orElseThrow();
    assertThat(savedReply.getRootId()).isEqualTo(root);
    assertThat(savedReply.getParentId()).isEqualTo(root);
    assertThat(comments.find(reply + 1_000)).isEmpty();
  }

  /// 改正文只动正文、曾经 @ 过的人和版本时刻。
  @Test
  void should_update_body_mentions_and_revised_at_only() {
    long id = insert(null, null, 1L, "旧", List.of(7L), T0);
    Instant revised = T0.plus(1, ChronoUnit.HOURS);
    CommentAggregate edited =
        comments.find(id).orElseThrow().toBuilder()
            .body("新")
            .mentionedUserIds(Set.of(7L, 8L))
            .revisedAt(revised)
            .build();

    transactions.executeWithoutResult(s -> comments.updateBody(edited));

    CommentAggregate saved = comments.find(id).orElseThrow();
    assertThat(saved.getBody()).isEqualTo("新");
    assertThat(saved.getMentionedUserIds()).containsExactlyInAnyOrder(7L, 8L);
    assertThat(saved.getRevisedAt()).isEqualTo(revised);
    assertThat(saved.getCreatedAt()).isEqualTo(T0);
  }

  /// 新评论的正文版本是 1（PAP-141）。
  @Test
  void should_insert_with_initial_version() {
    long id = insert(null, null, 1L, "根", List.of(), T0);

    assertThat(comments.find(id).orElseThrow().getVersion())
        .isEqualTo(CommentAggregate.INITIAL_VERSION);
  }

  /// 改正文把加一后的版本一起写进去：连改两次是 3。
  @Test
  void should_persist_incremented_version() {
    long id = insert(null, null, 1L, "旧", List.of(), T0);

    for (String body : List.of("二", "三")) {
      transactions.executeWithoutResult(
          s -> {
            CommentAggregate locked = comments.findForEdit(id).orElseThrow();
            comments.updateBody(
                locked.edit(1L, body, List.of(), T0.plusSeconds(10)).orElseThrow().updated());
          });
    }

    CommentAggregate saved = comments.find(id).orElseThrow();
    assertThat(saved.getVersion()).isEqualTo(3);
    assertThat(saved.getBody()).isEqualTo("三");
  }

  /// 修改历史的版本号依次递增。
  @Test
  void should_number_revisions_in_order() {
    long id = insert(null, null, 1L, "当前", List.of(), T0);

    transactions.executeWithoutResult(
        s -> {
          comments.appendRevision(id, RevisionRecord.of("第一版", T0));
          comments.appendRevision(id, RevisionRecord.of("第二版", T0.plusSeconds(600)));
        });

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT revision_no, body FROM discussion.comment_revision WHERE comment_id = ?"
                + " ORDER BY revision_no",
            id);
    assertThat(rows)
        .extracting(r -> r.get("revision_no"), r -> r.get("body"))
        .containsExactly(tuple(1, "第一版"), tuple(2, "第二版"));
  }

  /// 变占位：清正文、清提及、记删除时刻，修改历史一起删掉。
  @Test
  void should_mark_placeholder_and_drop_revisions() {
    long id = insert(null, null, 1L, "当前", List.of(7L), T0);
    transactions.executeWithoutResult(s -> comments.appendRevision(id, RevisionRecord.of("旧", T0)));
    Instant now = T0.plusSeconds(60);

    transactions.executeWithoutResult(s -> comments.markDeleted(id, now));

    CommentAggregate saved = comments.find(id).orElseThrow();
    assertThat(saved.getState()).isEqualTo(CommentState.DELETED);
    assertThat(saved.getBody()).isNull();
    assertThat(saved.getMentionedUserIds()).isEmpty();
    assertThat(revisionCount(id)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM discussion.comment WHERE comment_id = ?",
                Boolean.class,
                id))
        .isTrue();
  }

  /// 删除评论，修改历史随外键一起没了。
  @Test
  void should_delete_comment_with_its_revisions() {
    long id = insert(null, null, 1L, "当前", List.of(), T0);
    transactions.executeWithoutResult(s -> comments.appendRevision(id, RevisionRecord.of("旧", T0)));

    transactions.executeWithoutResult(s -> comments.delete(id));

    assertThat(comments.find(id)).isEmpty();
    assertThat(revisionCount(id)).isZero();
  }

  /// 有没有直接回复。
  @Test
  void should_report_direct_replies() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    long reply = insert(root, root, 2L, "回复", List.of(), T0.plusSeconds(1));

    assertThat(comments.hasReplies(root)).isTrue();
    assertThat(comments.hasReplies(reply)).isFalse();
  }

  /// 还有回复的评论删不掉（外键兜底）。
  @Test
  void should_refuse_to_delete_comment_with_replies() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    insert(root, root, 2L, "回复", List.of(), T0.plusSeconds(1));

    assertThatThrownBy(() -> transactions.executeWithoutResult(s -> comments.delete(root)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /// 楼锁：别的连接持有同一楼的锁时，`lockThread` 一直等到它释放。
  @Test
  void should_block_on_thread_lock_held_elsewhere() throws Exception {
    long rootId = 4242L;
    try (Connection holder = dataSource.getConnection()) {
      try (PreparedStatement lock =
          holder.prepareStatement("SELECT pg_advisory_lock(hashtextextended(?, 0))")) {
        lock.setString(1, "discussion.thread:" + rootId);
        lock.execute();
      }
      try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
        CompletableFuture<Void> locked =
            CompletableFuture.runAsync(
                () -> transactions.executeWithoutResult(s -> comments.lockThread(rootId)), pool);
        awaitBlockedOnLock();
        assertThat(locked).isNotDone();
        try (PreparedStatement unlock =
            holder.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 0))")) {
          unlock.setString(1, "discussion.thread:" + rootId);
          unlock.execute();
        }
        locked.get();
      }
    }
  }

  /// 楼统计的重算：回复数或最后回复时间不对、缺行、根评论已没有的行都找得到；拿楼锁后改成明细值并带回回复数的前后值，
  /// 根评论没有了就删行；修完再找为空。
  @Test
  void should_find_and_fix_thread_stats_drift() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    insert(root, root, 2L, "一", List.of(), T0.plus(1, ChronoUnit.HOURS));
    long second = insert(root, root, 3L, "二", List.of(), T0.plus(2, ChronoUnit.HOURS));
    transactions.executeWithoutResult(
        s -> comments.markDeleted(second, T0.plus(3, ChronoUnit.HOURS)));
    long other = insert(null, null, 1L, "另一楼", List.of(), T0);
    insert(other, other, 2L, "回", List.of(), T0.plus(4, ChronoUnit.HOURS));
    jdbcTemplate.update(
        "INSERT INTO discussion.thread_stats (root_id, reply_count, last_reply_at) VALUES"
            + " (?, 5, ?), (999, 2, ?)",
        root,
        Timestamp.from(T0.plus(2, ChronoUnit.HOURS)),
        Timestamp.from(T0));

    assertThat(comments.findThreadStatsDrift()).containsExactlyInAnyOrder(root, other, 999L);

    assertThat(recountThread(root)).contains(StatsCorrection.of(5, 1));
    assertThat(threadStats(root)).containsExactly(1, T0.plus(1, ChronoUnit.HOURS));
    assertThat(recountThread(root)).isEmpty();
    assertThat(recountThread(other)).contains(StatsCorrection.of(0, 1));
    assertThat(threadStats(other)).containsExactly(1, T0.plus(4, ChronoUnit.HOURS));
    assertThat(recountThread(999L)).contains(StatsCorrection.of(2, 0));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM discussion.thread_stats WHERE root_id = 999", Long.class))
        .isZero();
    assertThat(comments.findThreadStatsDrift()).isEmpty();
  }

  /// 最后回复时间不对、回复数对，也算差异并修正（修正里回复数前后相同）。
  @Test
  void should_fix_last_reply_time_only_drift() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    insert(root, root, 2L, "一", List.of(), T0.plus(1, ChronoUnit.HOURS));
    jdbcTemplate.update(
        "INSERT INTO discussion.thread_stats (root_id, reply_count, last_reply_at) VALUES (?, 1, ?)",
        root,
        Timestamp.from(T0));

    assertThat(comments.findThreadStatsDrift()).containsExactly(root);
    assertThat(recountThread(root)).contains(StatsCorrection.of(1, 1));
    assertThat(threadStats(root)).containsExactly(1, T0.plus(1, ChronoUnit.HOURS));
  }

  /// 对象评论总数的重算：只数未删除的（占位不算）；多出来的统计行置 0、保留；修完再找为空。
  @Test
  void should_find_and_fix_comment_count_drift() {
    long root = insert(null, null, 1L, "根", List.of(), T0);
    insert(root, root, 2L, "一", List.of(), T0);
    long gone = insert(root, root, 3L, "二", List.of(), T0);
    transactions.executeWithoutResult(s -> comments.markDeleted(gone, T0));
    jdbcTemplate.update(
        "INSERT INTO discussion.subject_stats (subject_id, comment_count) VALUES (11, 7), (12, 3)");

    assertThat(comments.findCommentCountDrift()).containsExactlyInAnyOrder(S1, S2);

    assertThat(recountSubject(S1)).contains(StatsCorrection.of(7, 2));
    assertThat(recountSubject(S1)).isEmpty();
    assertThat(recountSubject(S2)).contains(StatsCorrection.of(3, 0));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT comment_count FROM discussion.subject_stats WHERE subject_id = 12",
                Integer.class))
        .isZero();
    assertThat(comments.findCommentCountDrift()).isEmpty();
  }

  /// 两项的锁内修正都要在事务里调用。
  @Test
  void should_require_transaction_for_recount() {
    assertThatThrownBy(() -> comments.recountThreadStats(1L))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> comments.recountCommentCount(S1))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  /// 拿楼锁后重算楼统计（与重算任务的调用方式一致）。
  ///
  /// @param rootId 根评论
  /// @return 修正
  private Optional<StatsCorrection> recountThread(long rootId) {
    Optional<StatsCorrection> correction =
        transactions.execute(
            s -> {
              comments.lockThread(rootId);
              return comments.recountThreadStats(rootId);
            });
    return correction;
  }

  /// 在事务里重算对象评论总数。
  ///
  /// @param subjectId 对象
  /// @return 修正
  private Optional<StatsCorrection> recountSubject(SubjectId subjectId) {
    Optional<StatsCorrection> correction =
        transactions.execute(s -> comments.recountCommentCount(subjectId));
    return correction;
  }

  /// 在事务里插入一条评论。
  ///
  /// @param rootId 根评论，根评论为 `null`
  /// @param parentId 父评论，根评论为 `null`
  /// @param authorId 作者
  /// @param body 正文
  /// @param mentions 提及
  /// @param createdAt 发表时刻
  /// @return 新评论 ID
  private long insert(
      Long rootId,
      Long parentId,
      long authorId,
      String body,
      List<Long> mentions,
      Instant createdAt) {
    NewComment comment =
        NewComment.builder()
            .subjectId(S1)
            .rootId(rootId)
            .parentId(parentId)
            .authorId(authorId)
            .body(body)
            .mentionedUserIds(mentions)
            .createdAt(createdAt)
            .build();
    return transactions.execute(s -> comments.insert(comment));
  }

  /// 一楼的统计：回复数与最后回复时间。
  ///
  /// @param rootId 根评论
  /// @return `[回复数, 最后回复时间]`，时间为空时第二项为 `null`
  private List<Object> threadStats(long rootId) {
    return jdbcTemplate.queryForObject(
        "SELECT reply_count, last_reply_at FROM discussion.thread_stats WHERE root_id = ?",
        (rs, rowNum) -> {
          Timestamp last = rs.getTimestamp("last_reply_at");
          return Arrays.asList(rs.getInt("reply_count"), last == null ? null : last.toInstant());
        },
        rootId);
  }

  /// 某条评论的修改历史行数。
  ///
  /// @param commentId 评论
  /// @return 行数
  private long revisionCount(long commentId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM discussion.comment_revision WHERE comment_id = ?",
        Long.class,
        commentId);
  }

  /// 等到有会话在等 advisory lock。
  ///
  /// @throws InterruptedException 被中断
  private void awaitBlockedOnLock() throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      Long waiting =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                  + " AND wait_event = 'advisory'",
              Long.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("没有等到 advisory lock 上的等待");
  }
}
