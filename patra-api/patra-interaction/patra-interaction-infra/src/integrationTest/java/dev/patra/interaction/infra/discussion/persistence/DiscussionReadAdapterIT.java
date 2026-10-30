package dev.patra.interaction.infra.discussion.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.discussion.model.enums.CommentState;
import dev.patra.interaction.domain.discussion.model.enums.ThreadSort;
import dev.patra.interaction.domain.discussion.model.read.CommentRecord;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.discussion.model.read.ThreadStats;
import dev.patra.interaction.domain.discussion.model.vo.NewComment;
import dev.patra.interaction.domain.discussion.model.vo.ThreadCursor;
import dev.patra.interaction.domain.discussion.port.read.DiscussionReadPort;
import dev.patra.interaction.domain.discussion.port.repository.CommentRepository;
import dev.patra.interaction.domain.subject.model.vo.RankCursor;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/// 讨论的读端：根评论分页（三种排序、游标）、每楼预览、楼内分页、整楼、祖先链、修改历史、最近参与者、「我点过」、
/// 楼统计与对象评论数；每条都带父评论的作者与状态、有没有直接回复与有帮助数。
class DiscussionReadAdapterIT extends DiscussionPersistenceITSupport {

  @Autowired private DiscussionReadPort reads;
  @Autowired private CommentRepository comments;

  /// 同一微秒的两条按 ID 排；两种排序各自翻页。
  @Test
  void should_page_roots_in_both_orders_with_cursor() {
    long r1 = root(S1, 1L, T0);
    long r2 = root(S1, 1L, T0);
    long r3 = root(S1, 1L, T0.plusSeconds(1));
    root(S2, 1L, T0);

    List<CommentRecord> first = reads.findRoots(S1, ThreadSort.ASC, null, 2);
    assertThat(ids(first)).containsExactly(r1, r2);
    assertThat(ids(reads.findRoots(S1, ThreadSort.ASC, cursorOf(first.get(1)), 2)))
        .containsExactly(r3);

    List<CommentRecord> latest = reads.findRoots(S1, ThreadSort.LATEST, null, 2);
    assertThat(ids(latest)).containsExactly(r3, r2);
    assertThat(ids(reads.findRoots(S1, ThreadSort.LATEST, cursorOf(latest.get(1)), 2)))
        .containsExactly(r1);
  }

  /// 翻页之间删掉第一页里的一条，第二页照游标继续，不重复也不漏。
  @Test
  void should_not_skip_or_repeat_when_a_root_is_deleted_between_pages() {
    long r1 = root(S1, 1L, T0);
    long r2 = root(S1, 1L, T0.plusSeconds(1));
    long r3 = root(S1, 1L, T0.plusSeconds(2));
    List<CommentRecord> first = reads.findRoots(S1, ThreadSort.ASC, null, 2);

    transactions.executeWithoutResult(s -> comments.delete(r1));

    assertThat(ids(first)).containsExactly(r1, r2);
    assertThat(ids(reads.findRoots(S1, ThreadSort.ASC, cursorOf(first.get(1)), 2)))
        .containsExactly(r3);
  }

  /// 每楼最早的若干条回复；没有回复的根不在结果里。
  @Test
  void should_preview_earliest_replies_per_root() {
    long a = root(S1, 1L, T0);
    long b = root(S1, 1L, T0.plusSeconds(1));
    long c = root(S1, 1L, T0.plusSeconds(2));
    List<Long> aReplies = replies(a, 5);
    long bReply = reply(b, b, 2L, T0.plusSeconds(100));

    Map<Long, List<CommentRecord>> previews = reads.findReplyPreviews(List.of(a, b, c), 4);

    assertThat(ids(previews.get(a))).containsExactlyElementsOf(aReplies.subList(0, 4));
    assertThat(ids(previews.get(b))).containsExactly(bReply);
    assertThat(previews).doesNotContainKey(c);
    assertThat(reads.findReplyPreviews(List.of(), 4)).isEmpty();
  }

  /// 楼内回复按游标往后取。
  @Test
  void should_page_replies_after_cursor() {
    long a = root(S1, 1L, T0);
    List<Long> aReplies = replies(a, 5);
    CommentRecord third = reads.findById(aReplies.get(2)).orElseThrow();

    assertThat(ids(reads.findReplies(a, null, 2)))
        .containsExactlyElementsOf(aReplies.subList(0, 2));
    assertThat(ids(reads.findReplies(a, cursorOf(third), 50)))
        .containsExactlyElementsOf(aReplies.subList(3, 5));
  }

  /// 整楼：根在前，回复按时间正序。
  @Test
  void should_return_whole_thread_root_first() {
    long a = root(S1, 1L, T0);
    List<Long> aReplies = replies(a, 3);

    assertThat(ids(reads.findThread(a)))
        .containsExactly(a, aReplies.get(0), aReplies.get(1), aReplies.get(2));
  }

  /// 祖先链从根到它，占位也在链上。
  @Test
  void should_return_ancestry_from_root_including_placeholders() {
    long r = root(S1, 1L, T0);
    long x = reply(r, r, 2L, T0.plusSeconds(1));
    long y = reply(r, x, 3L, T0.plusSeconds(2));
    long z = reply(r, y, 4L, T0.plusSeconds(3));
    transactions.executeWithoutResult(s -> comments.markDeleted(x, T0.plusSeconds(10)));

    assertThat(ids(reads.findAncestry(z))).containsExactly(r, x, y, z);
  }

  /// 每条带父评论的作者与状态、有没有直接回复；`findById` 查不到为空。
  @Test
  void should_carry_parent_author_state_and_replies_flag() {
    long r = root(S1, 1L, T0);
    long x = reply(r, r, 2L, T0.plusSeconds(1));
    long y = reply(r, x, 3L, T0.plusSeconds(2));
    transactions.executeWithoutResult(s -> comments.markDeleted(x, T0.plusSeconds(10)));

    CommentRecord yRecord = reads.findById(y).orElseThrow();
    assertThat(yRecord.parentAuthorId()).isEqualTo(2L);
    assertThat(yRecord.parentState()).isEqualTo(CommentState.DELETED);
    assertThat(yRecord.hasReplies()).isFalse();
    assertThat(yRecord.subjectId()).isEqualTo(S1);
    assertThat(yRecord.createdAt()).isEqualTo(T0.plusSeconds(2));
    CommentRecord xRecord = reads.findById(x).orElseThrow();
    assertThat(xRecord.hasReplies()).isTrue();
    assertThat(xRecord.isDeleted()).isTrue();
    assertThat(xRecord.body()).isNull();
    CommentRecord rRecord = reads.findById(r).orElseThrow();
    assertThat(rRecord.parentAuthorId()).isNull();
    assertThat(rRecord.parentState()).isNull();
    assertThat(reads.findById(r + 1_000)).isEmpty();
  }

  /// 修改历史倒序。
  @Test
  void should_list_revisions_newest_first() {
    long r = root(S1, 1L, T0);
    transactions.executeWithoutResult(
        s -> {
          comments.appendRevision(r, RevisionRecord.of("第一版", T0));
          comments.appendRevision(r, RevisionRecord.of("第二版", T0.plusSeconds(600)));
        });

    assertThat(reads.findRevisions(r))
        .containsExactly(
            RevisionRecord.of("第二版", T0.plusSeconds(600)), RevisionRecord.of("第一版", T0));
  }

  /// 最近参与者：只算正常评论的作者，排除给定 ID，按各自最近一条倒序。
  @Test
  void should_list_recent_participants() {
    root(S1, 1L, T0);
    long two = root(S1, 2L, T0.plusSeconds(10));
    long three = root(S1, 3L, T0.plusSeconds(5));
    reply(two, two, 1L, T0.plusSeconds(20));
    reply(two, two, 4L, T0.plusSeconds(15));
    root(S2, 5L, T0.plusSeconds(30));
    transactions.executeWithoutResult(s -> comments.markDeleted(three, T0.plusSeconds(40)));

    assertThat(reads.findRecentParticipants(S1, List.of(), 10)).containsExactly(1L, 4L, 2L);
    assertThat(reads.findRecentParticipants(S1, List.of(4L), 10)).containsExactly(1L, 2L);
    assertThat(reads.findRecentParticipants(S1, List.of(), 1)).containsExactly(1L);
  }

  /// 发一条根评论。
  ///
  /// @param subject 对象
  /// @param author 作者
  /// @param at 发表时刻
  /// @return 评论 ID
  private long root(SubjectId subject, long author, Instant at) {
    return insert(subject, null, null, author, at);
  }

  /// 发一条回复。
  ///
  /// @param rootId 根评论
  /// @param parentId 父评论
  /// @param author 作者
  /// @param at 发表时刻
  /// @return 评论 ID
  private long reply(long rootId, long parentId, long author, Instant at) {
    return insert(S1, rootId, parentId, author, at);
  }

  /// 在一楼里按时间依次发若干条回复根的回复。
  ///
  /// @param rootId 根评论
  /// @param count 条数
  /// @return 回复 ID，按时间顺序
  private List<Long> replies(long rootId, int count) {
    return IntStream.rangeClosed(1, count)
        .mapToObj(i -> reply(rootId, rootId, 10L + i, T0.plusSeconds(i)))
        .toList();
  }

  /// 每条评论带有帮助数；没有统计行为 0。
  @Test
  void should_carry_helpful_count() {
    long r1 = root(S1, 1L, T0);
    long r2 = root(S1, 1L, T0.plusSeconds(1));
    helpful(r1, 3);

    assertThat(reads.findById(r1).orElseThrow().helpfulCount()).isEqualTo(3);
    assertThat(reads.findById(r2).orElseThrow().helpfulCount()).isZero();
    assertThat(reads.findRoots(S1, ThreadSort.ASC, null, 10))
        .extracting(CommentRecord::helpfulCount)
        .containsExactly(3, 0);
  }

  /// 「最有帮助」：有帮助数降序，相同时新的在前；按三元组游标逐页翻完，不重复不遗漏；只看本对象的根评论。
  @Test
  void should_order_roots_by_helpful_then_newest() {
    long r1 = root(S1, 1L, T0);
    long r2 = root(S1, 1L, T0.plusSeconds(1));
    long r3 = root(S1, 1L, T0.plusSeconds(2));
    long r4 = root(S1, 1L, T0);
    long other = root(S2, 1L, T0);
    reply(r3, r3, 2L, T0.plusSeconds(3));
    helpful(r1, 2);
    helpful(r2, 2);
    helpful(r4, 5);
    helpful(other, 9);

    assertThat(ids(reads.findRootsByHelpful(S1, null, 10))).containsExactly(r4, r2, r1, r3);
    List<Long> paged = new ArrayList<>();
    RankCursor after = null;
    for (int page = 0; page < 5; page++) {
      List<CommentRecord> one = reads.findRootsByHelpful(S1, after, 1);
      if (one.isEmpty()) {
        break;
      }
      CommentRecord last = one.getLast();
      paged.add(last.commentId());
      after = RankCursor.of(last.helpfulCount(), last.createdAt(), last.commentId());
    }
    assertThat(paged).containsExactly(r4, r2, r1, r3);
  }

  /// 「我点过」只看这个访客的明细。
  @Test
  void should_find_helpful_marked_by_viewer() {
    long c1 = root(S1, 1L, T0);
    long c2 = root(S1, 1L, T0);
    long c3 = root(S1, 1L, T0);
    marked(7L, c1);
    marked(8L, c2);

    assertThat(reads.findHelpfulMarked(7L, List.of(c1, c2, c3))).containsExactly(c1);
    assertThat(reads.findHelpfulMarked(7L, List.of())).isEmpty();
  }

  /// 楼统计只返回有行的楼；对象评论数没有行为 0。
  @Test
  void should_find_thread_stats_and_comment_count() {
    jdbcTemplate.update(
        "INSERT INTO discussion.thread_stats VALUES (40, 2, ?), (41, 0, NULL)", Timestamp.from(T0));
    jdbcTemplate.update("INSERT INTO discussion.subject_stats VALUES (?, 5)", S1.value());

    assertThat(reads.findThreadStats(List.of(40L, 41L, 42L)))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(40L, ThreadStats.of(2, T0), 41L, ThreadStats.of(0, null)));
    assertThat(reads.findThreadStats(List.of())).isEmpty();
    assertThat(reads.countComments(S1)).isEqualTo(5);
    assertThat(reads.countComments(S2)).isZero();
  }

  /// 直接写一条评论的有帮助数。
  ///
  /// @param commentId 评论
  /// @param count 有帮助数
  private void helpful(long commentId, int count) {
    jdbcTemplate.update(
        "INSERT INTO reaction.target_stats (target_type, target_id, helpful_count)"
            + " VALUES ('COMMENT', ?, ?)",
        commentId,
        count);
  }

  /// 直接写一条有帮助明细。
  ///
  /// @param userId 点的人
  /// @param commentId 评论
  private void marked(long userId, long commentId) {
    jdbcTemplate.update(
        "INSERT INTO reaction.helpful (user_id, target_type, target_id, credited_user_id,"
            + " created_at) VALUES (?, 'COMMENT', ?, NULL, now())",
        userId,
        commentId);
  }

  /// 在事务里插入。
  ///
  /// @param subject 对象
  /// @param rootId 根评论，根评论为 `null`
  /// @param parentId 父评论，根评论为 `null`
  /// @param author 作者
  /// @param at 发表时刻
  /// @return 评论 ID
  private long insert(SubjectId subject, Long rootId, Long parentId, long author, Instant at) {
    NewComment comment =
        NewComment.builder()
            .subjectId(subject)
            .rootId(rootId)
            .parentId(parentId)
            .authorId(author)
            .body("正文")
            .mentionedUserIds(List.of())
            .createdAt(at)
            .build();
    return transactions.execute(s -> comments.insert(comment));
  }

  /// 一条评论的游标。
  ///
  /// @param record 评论
  /// @return 游标
  private static ThreadCursor cursorOf(CommentRecord record) {
    return ThreadCursor.of(record.createdAt(), record.commentId());
  }

  /// 取 ID。
  ///
  /// @param records 评论
  /// @return ID
  private static List<Long> ids(List<CommentRecord> records) {
    return records.stream().map(CommentRecord::commentId).toList();
  }
}
