package dev.patra.interaction.discussion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 讨论串统计与「最有帮助」排序（PAP-132 的 AC）：楼内回复数与最后回复时间、对象评论总数、占位不计数、
/// 有帮助数与「我点过」、`sort=helpful`。identity 与 catalog 由 WireMock 扮演，库是真实 PG。
class DiscussionStatsFlowIT extends DiscussionFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long C = 1003L;
  private static final long PAPER = 100L;
  private static final String THREADS = "/discussions/publication/" + PAPER + "/threads";

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B, C);
  }

  /// AC：发回复、删回复后楼内回复数与最后回复时间正确；删掉最新的一条回退到上一条；删光为空；总数随之变化。
  @Test
  void should_track_reply_count_and_last_reply_time() {
    long root = comment(A, PAPER, "根", null);
    long r1 = comment(B, PAPER, "1", root);
    long r2 = comment(C, PAPER, "2", root);
    long r3 = comment(B, PAPER, "3", root);

    JsonNode page = get(null, THREADS);
    assertThat(page.get("total").asInt()).isEqualTo(4);
    assertThat(firstThread(page).get("replyCount").asInt()).isEqualTo(3);
    assertThat(lastReplyAt(firstThread(page))).isEqualTo(createdAt(page, r3));

    delete(B, r3);
    page = get(null, THREADS);
    assertThat(page.get("total").asInt()).isEqualTo(3);
    assertThat(firstThread(page).get("replyCount").asInt()).isEqualTo(2);
    assertThat(lastReplyAt(firstThread(page))).isEqualTo(createdAt(page, r2));

    delete(C, r2);
    delete(B, r1);
    page = get(null, THREADS);
    assertThat(page.get("total").asInt()).isEqualTo(1);
    assertThat(firstThread(page).get("replyCount").asInt()).isZero();
    assertThat(firstThread(page).get("lastReplyAt").isNull()).isTrue();
  }

  /// AC：变成「该评论已删除」占位的评论不计入回复数与讨论数；占位根评论照样带楼统计。
  @Test
  void should_exclude_placeholders_from_counts() {
    long root = comment(A, PAPER, "根", null);
    long b = comment(B, PAPER, "b", root);
    comment(C, PAPER, "c", b);

    delete(A, root);
    JsonNode page = get(null, THREADS);
    assertThat(page.get("total").asInt()).isEqualTo(2);
    JsonNode thread = firstThread(page);
    assertThat(thread.get("root").get("deleted").asBoolean()).isTrue();
    assertThat(thread.get("replyCount").asInt()).isEqualTo(2);

    delete(B, b);
    page = get(null, THREADS);
    assertThat(page.get("total").asInt()).isEqualTo(1);
    assertThat(firstThread(page).get("replyCount").asInt()).isEqualTo(1);
  }

  /// 根评论先变占位，删光回复时级联去掉：总数只减各自那一次，楼统计行随根一起没了。
  @Test
  void should_cascade_root_removal_without_double_decrement() {
    long root = comment(A, PAPER, "根", null);
    long reply = comment(B, PAPER, "回复", root);

    delete(A, root);
    assertThat(get(null, THREADS).get("total").asInt()).isEqualTo(1);

    delete(B, reply);
    JsonNode page = get(null, THREADS);
    assertThat(page.get("items")).isEmpty();
    assertThat(page.get("total").asInt()).isZero();
    assertThat(count("SELECT count(*) FROM discussion.thread_stats WHERE root_id = ?", root))
        .isZero();
  }

  /// AC：`sort=helpful` 下根评论按自己的有帮助数降序（楼内回复的不计入），楼内回复仍按时间正序；逐页翻不重复。
  @Test
  void should_sort_by_helpful_with_replies_in_time_order() {
    long first = comment(A, PAPER, "第一楼", null);
    long firstReply1 = comment(C, PAPER, "回复 1", first);
    long firstReply2 = comment(A, PAPER, "回复 2", first);
    long second = comment(A, PAPER, "第二楼", null);
    long third = comment(A, PAPER, "第三楼", null);
    mark(B, second);
    mark(C, second);
    mark(B, third);
    mark(B, firstReply1);

    JsonNode page = get(null, THREADS + "?sort=helpful");
    assertThat(rootIds(page)).containsExactly(second, third, first);
    assertThat(ids(page.get("items").get(2).get("replies")))
        .containsExactly(firstReply1, firstReply2);

    List<Long> paged = new ArrayList<>();
    String cursor = null;
    do {
      JsonNode one =
          get(null, THREADS + "?sort=helpful&size=1" + (cursor == null ? "" : "&cursor=" + cursor));
      paged.addAll(rootIds(one));
      cursor = one.get("nextCursor").isNull() ? null : one.get("nextCursor").asString();
    } while (cursor != null);
    assertThat(paged).containsExactly(second, third, first);
  }

  /// AC：「我点过没有」只在有会话时返回且与明细一致；有帮助数与明细条数一致。
  @Test
  void should_return_helpful_only_with_session() {
    long root = comment(A, PAPER, "根", null);
    long reply = comment(C, PAPER, "回复", root);
    mark(B, root);
    mark(C, root);

    JsonNode anonymous = firstThread(get(null, THREADS));
    assertThat(anonymous.get("root").get("helpful").isNull()).isTrue();
    assertThat(anonymous.get("root").get("helpfulCount").asInt()).isEqualTo(2);

    JsonNode signedIn = firstThread(get(B, THREADS));
    assertThat(signedIn.get("root").get("helpful").asBoolean()).isTrue();
    assertThat(signedIn.get("replies").get(0).get("helpful").asBoolean()).isFalse();
    assertThat(signedIn.get("replies").get(0).get("helpfulCount").asInt()).isZero();
    assertThat(signedIn.get("root").get("helpfulCount").asLong())
        .isEqualTo(
            count(
                "SELECT count(*) FROM reaction.helpful WHERE target_type = 'COMMENT'"
                    + " AND target_id = ?",
                root));
    mark(B, reply);
    JsonNode replies = get(B, "/comments/" + root + "/replies").get("items");
    assertThat(idOf(replies.get(0), "commentId")).isEqualTo(reply);
    assertThat(replies.get(0).get("helpful").asBoolean()).isTrue();
    assertThat(replies.get(0).get("helpfulCount").asInt()).isEqualTo(1);
    JsonNode guestReplies = get(null, "/comments/" + root + "/replies").get("items");
    assertThat(guestReplies.get(0).get("helpful").isNull()).isTrue();
  }

  /// 单楼视图同样带楼统计。
  @Test
  void should_carry_thread_stats_in_single_thread_view() {
    long root = comment(A, PAPER, "根", null);
    comment(B, PAPER, "1", root);
    long last = comment(C, PAPER, "2", root);

    JsonNode thread =
        get(null, "/discussions/publication/" + PAPER + "/comments/" + last + "/thread")
            .get("thread");

    assertThat(thread.get("replyCount").asInt()).isEqualTo(2);
    assertThat(lastReplyAt(thread))
        .isEqualTo(Instant.parse(thread.get("replies").get(1).get("createdAt").asString()));
  }

  /// 点有帮助。
  ///
  /// @param user 点的人
  /// @param commentId 评论
  private void mark(long user, long commentId) {
    assertThat(status(call(HttpMethod.PUT, user, "/comments/" + commentId + "/helpful", null)))
        .isEqualTo(200);
  }

  /// 删除自己的评论。
  ///
  /// @param user 作者
  /// @param commentId 评论
  private void delete(long user, long commentId) {
    assertThat(status(call(HttpMethod.DELETE, user, "/comments/" + commentId, null)))
        .isEqualTo(200);
  }

  /// 第一楼。
  ///
  /// @param page 列表页
  /// @return 楼
  private static JsonNode firstThread(JsonNode page) {
    return page.get("items").get(0);
  }

  /// 每楼根评论的 ID。
  ///
  /// @param page 列表页
  /// @return ID
  private static List<Long> rootIds(JsonNode page) {
    return page.get("items").valueStream().map(t -> idOf(t.get("root"), "commentId")).toList();
  }

  /// 楼的最后回复时间。
  ///
  /// @param thread 楼
  /// @return 时刻
  private static Instant lastReplyAt(JsonNode thread) {
    return Instant.parse(thread.get("lastReplyAt").asString());
  }

  /// 第一楼里某条回复的发表时间。
  ///
  /// @param page 列表页
  /// @param replyId 回复
  /// @return 时刻
  private static Instant createdAt(JsonNode page, long replyId) {
    return firstThread(page)
        .get("replies")
        .valueStream()
        .filter(r -> idOf(r, "commentId") == replyId)
        .map(r -> Instant.parse(r.get("createdAt").asString()))
        .findFirst()
        .orElseThrow();
  }
}
