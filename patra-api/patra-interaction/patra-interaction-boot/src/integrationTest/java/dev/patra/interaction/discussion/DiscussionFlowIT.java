package dev.patra.interaction.discussion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 讨论的主流程（PAP-121 的 AC）：两层展示与「回复 @X」、翻页与展开、单楼与对话链、删除留占位与级联、回复已删除的
/// 评论、登录与权限、回复与删除同时发生时树保持一致。identity 与 catalog 由 WireMock 扮演，库是真实 PG。
class DiscussionFlowIT extends DiscussionFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long C = 1003L;
  private static final long D = 1004L;
  private static final long PAPER = 100L;
  private static final long OTHER_PAPER = 200L;
  private static final String THREADS = "/discussions/publication/" + PAPER + "/threads";

  @BeforeEach
  void world() {
    publication(PAPER);
    publication(OTHER_PAPER);
    users(A, B, C, D);
  }

  /// AC：三层以上的回复在楼内平铺，「回复 @X」指向正确；匿名访客也能读。
  @Test
  void should_show_two_layers_and_reply_targets() {
    long root = comment(A, PAPER, "样本量太小", null);
    long b = comment(B, PAPER, "同意", root);
    long c = comment(C, PAPER, "未必", b);
    long d = comment(D, PAPER, "补充", c);

    JsonNode thread = get(null, THREADS).get("items").get(0);

    assertThat(idOf(thread.get("root"), "commentId")).isEqualTo(root);
    JsonNode replies = thread.get("replies");
    assertThat(ids(replies)).containsExactly(b, c, d);
    assertThat(replies.get(0).get("replyTo").isNull()).isTrue();
    assertThat(idOf(replies.get(1).get("replyTo").get("author"), "userId")).isEqualTo(B);
    assertThat(idOf(replies.get(2).get("replyTo").get("author"), "userId")).isEqualTo(C);
    assertThat(thread.get("hasMoreReplies").asBoolean()).isFalse();
    assertThat(thread.get("root").get("author").get("username").asString()).isEqualTo("user-" + A);
  }

  /// 两楼、每页一楼翻到第二楼；一楼 5 条回复先给 3 条，按游标取剩下 2 条。
  @Test
  void should_page_threads_and_expand_more_replies() {
    long first = comment(A, PAPER, "第一楼", null);
    List<Long> replies =
        List.of(
            comment(B, PAPER, "1", first),
            comment(B, PAPER, "2", first),
            comment(B, PAPER, "3", first),
            comment(B, PAPER, "4", first),
            comment(B, PAPER, "5", first));
    long second = comment(A, PAPER, "第二楼", null);

    JsonNode page = get(A, THREADS + "?size=1");
    JsonNode thread = page.get("items").get(0);
    assertThat(idOf(thread.get("root"), "commentId")).isEqualTo(first);
    assertThat(ids(thread.get("replies"))).containsExactlyElementsOf(replies.subList(0, 3));
    assertThat(thread.get("hasMoreReplies").asBoolean()).isTrue();

    JsonNode next = get(A, THREADS + "?size=1&cursor=" + page.get("nextCursor").asString());
    assertThat(idOf(next.get("items").get(0).get("root"), "commentId")).isEqualTo(second);

    JsonNode more =
        get(A, "/comments/" + first + "/replies?cursor=" + thread.get("repliesCursor").asString());
    assertThat(ids(more.get("items"))).containsExactlyElementsOf(replies.subList(3, 5));
    assertThat(more.get("nextCursor").isNull()).isTrue();
  }

  /// 单楼按目标取整楼；换个对象 404 并带原因码；对话链从根到它。
  @Test
  void should_open_single_thread_and_conversation() {
    long root = comment(A, PAPER, "根", null);
    long b = comment(B, PAPER, "b", root);
    long c = comment(C, PAPER, "c", b);
    long d = comment(D, PAPER, "d", c);

    JsonNode single = get(null, "/discussions/publication/" + PAPER + "/comments/" + d + "/thread");
    assertThat(idOf(single, "targetId")).isEqualTo(d);
    assertThat(ids(single.get("thread").get("replies"))).containsExactly(b, c, d);

    EntityExchangeResult<String> elsewhere =
        call(
            HttpMethod.GET,
            null,
            "/discussions/publication/" + OTHER_PAPER + "/comments/" + d + "/thread",
            null);
    assertThat(status(elsewhere)).isEqualTo(404);
    assertThat(traits(elsewhere)).contains("COMMENT_NOT_FOUND");

    assertThat(ids(get(null, "/comments/" + d + "/conversation").get("items")))
        .containsExactly(root, b, c, d);
  }

  /// AC：有回复的留占位、楼中楼保留；没回复的整条去掉；占位的最后一条回复被删时占位一并去掉。
  @Test
  void should_leave_placeholder_or_remove_on_delete() {
    long root = comment(A, PAPER, "根", null);
    long x = comment(B, PAPER, "x", root);
    long y = comment(C, PAPER, "y", x);

    JsonNode rootDeleted = json(call(HttpMethod.DELETE, A, "/comments/" + root, null));
    assertThat(rootDeleted.get("placeholder").asBoolean()).isTrue();
    JsonNode xDeleted = json(call(HttpMethod.DELETE, B, "/comments/" + x, null));
    assertThat(xDeleted.get("placeholder").asBoolean()).isTrue();
    JsonNode thread = get(null, THREADS).get("items").get(0);
    assertThat(thread.get("root").get("deleted").asBoolean()).isTrue();
    assertThat(thread.get("root").get("body").isNull()).isTrue();
    assertThat(ids(thread.get("replies"))).containsExactly(x, y);

    JsonNode yDeleted = json(call(HttpMethod.DELETE, C, "/comments/" + y, null));
    assertThat(yDeleted.get("placeholder").asBoolean()).isFalse();
    assertThat(yDeleted.get("removedIds").valueStream().map(n -> Long.parseLong(n.asString())))
        .containsExactly(y, x, root);
    assertThat(get(null, THREADS).get("items")).isEmpty();
  }

  /// 回复已删除的回复 409；回复已成占位的根评论（「回复这一楼」）照常。
  @Test
  void should_reject_reply_to_deleted_reply_but_allow_reply_to_the_floor() {
    long root = comment(A, PAPER, "根", null);
    long x = comment(B, PAPER, "x", root);
    comment(C, PAPER, "y", x);
    call(HttpMethod.DELETE, B, "/comments/" + x, null);
    call(HttpMethod.DELETE, A, "/comments/" + root, null);

    EntityExchangeResult<String> toDeleted = post(D, "publication", PAPER, "回复 x", x);
    assertThat(status(toDeleted)).isEqualTo(409);
    assertThat(traits(toDeleted)).contains("REPLY_TARGET_GONE");

    assertThat(status(post(D, "publication", PAPER, "回复这一楼", root))).isEqualTo(201);
  }

  /// 匿名发表 401；删别人的 403；空正文 422；期刊不能讨论 422。
  @Test
  void should_enforce_login_ownership_and_validation() {
    long root = comment(A, PAPER, "根", null);

    assertThat(
            status(
                call(
                    HttpMethod.POST,
                    null,
                    "/discussions/publication/" + PAPER + "/comments",
                    "{\"body\":\"hi\"}")))
        .isEqualTo(401);
    assertThat(status(call(HttpMethod.DELETE, B, "/comments/" + root, null))).isEqualTo(403);
    EntityExchangeResult<String> blank = post(A, "publication", PAPER, "  ", null);
    assertThat(status(blank)).isEqualTo(422);
    assertThat(json(blank).get("errors").get(0).get("code").asString()).isEqualTo("REQUIRED");
    assertThat(status(call(HttpMethod.GET, null, "/discussions/journal/1/threads", null)))
        .isEqualTo(422);
  }

  /// 同一楼里「删 X」与「回复 X」同时发生：要么回复成功、X 变占位，要么 X 已去掉、回复 409；不会有孤儿回复。
  @Test
  void should_keep_tree_consistent_when_reply_races_delete() throws Exception {
    long root = comment(A, PAPER, "根", null);
    for (int round = 0; round < 10; round++) {
      long x = comment(A, PAPER, "x" + round, root);
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
        Future<Integer> delete =
            pool.submit(
                () -> {
                  start.await();
                  return status(call(HttpMethod.DELETE, A, "/comments/" + x, null));
                });
        Future<Integer> reply =
            pool.submit(
                () -> {
                  start.await();
                  return status(post(B, "publication", PAPER, "回复" + x, x));
                });
        start.countDown();
        assertThat(delete.get()).isEqualTo(200);
        int replied = reply.get();
        long xRows = count("SELECT count(*) FROM discussion.comment WHERE comment_id = ?", x);
        if (replied == 201) {
          assertThat(
                  count(
                      "SELECT count(*) FROM discussion.comment WHERE comment_id = ? AND state ="
                          + " 'DELETED'",
                      x))
              .isEqualTo(1L);
        } else {
          assertThat(replied).isEqualTo(409);
          assertThat(xRows).isZero();
        }
      }
    }
    assertThat(
            count(
                "SELECT count(*) FROM discussion.comment c WHERE c.parent_id IS NOT NULL AND NOT"
                    + " EXISTS (SELECT 1 FROM discussion.comment p WHERE p.comment_id ="
                    + " c.parent_id)"))
        .isZero();
  }
}
