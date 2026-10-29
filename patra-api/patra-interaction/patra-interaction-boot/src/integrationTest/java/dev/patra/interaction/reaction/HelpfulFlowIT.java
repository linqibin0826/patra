package dev.patra.interaction.reaction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.support.DiscussionEventRecorder;
import dev.patra.interaction.support.InteractionFlowITSupport;
import dev.patra.tool.security.test.TestIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 有帮助的完整流程（PAP-131 的 AC），经 HTTP、真实 PG、假 catalog 与假 identity：并发计数、重复与取消、资格与频率、
/// 不能给自己点、匿名不计获赞、删除联动与同事务回滚。
class HelpfulFlowIT extends InteractionFlowITSupport {

  /// 作者 A。
  private static final long A = 3001L;

  /// 读者 B。
  private static final long B = 3002L;

  /// 读者 C。
  private static final long C = 3003L;

  /// 邮箱未验证的 D。
  private static final long D = 3004L;

  /// 注销冷静期的 E。
  private static final long E = 3005L;

  /// 文献的 catalog ID。
  private static final long PAPER = 100L;

  /// 期刊的 catalog ID。
  private static final long JOURNAL = 200L;

  @LocalServerPort private int port;
  @Autowired private DataSource dataSource;

  private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

  /// 期刊下的经验各用不同的投稿日期（同一作者同一期刊同一天只能有一条）。
  private int nextExperienceDay;

  @BeforeEach
  void world() {
    CATALOG_DATA.put(PAPER, "1000100", "J", "2019");
    CATALOG_DATA.putJournal(JOURNAL, "0404402", "Journal 200", "J 200");
    for (long id : List.of(A, B, C)) {
      IDENTITY_DATA.put(id, "ACTIVE", true, "PUBLIC");
    }
    IDENTITY_DATA.put(D, "ACTIVE", false, "PUBLIC");
    IDENTITY_DATA.put(E, "PENDING_DELETION", true, "PUBLIC");
    nextExperienceDay = 30;
  }

  /// AC：20 个人同时给同一条评论点有帮助，计数等于明细条数，作者获赞同样是 20。
  @Test
  void should_count_equal_to_rows_under_concurrent_marks() throws Exception {
    long comment = comment(A, "publication", PAPER, "根", null);
    List<Long> readers = LongStream.rangeClosed(4001, 4020).boxed().toList();
    readers.forEach(id -> IDENTITY_DATA.put(id, "ACTIVE", true, "PUBLIC"));
    CountDownLatch start = new CountDownLatch(1);
    List<CompletableFuture<Integer>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(readers.size());
        HttpClient client = HttpClient.newHttpClient()) {
      for (long reader : readers) {
        results.add(
            CompletableFuture.supplyAsync(
                () -> {
                  await(start);
                  return sendWithoutRetry(client, reader, "/comments/" + comment + "/helpful")
                      .statusCode();
                },
                pool));
      }
      start.countDown();
      for (CompletableFuture<Integer> result : results) {
        assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(200);
      }
    }

    assertThat(rows("COMMENT", comment)).isEqualTo(20L);
    assertThat(targetCount("COMMENT", comment)).isEqualTo(20L);
    assertThat(received(A)).isEqualTo(20L);
  }

  /// AC：重复点不重复计数、不多耗额度；取消减 1；再取消、取消没点过的都不报错、计数不变。
  @Test
  void should_not_double_count_and_should_unmark() {
    long comment = comment(A, "publication", PAPER, "根", null);
    String path = "/comments/" + comment + "/helpful";

    assertHelpful(call(HttpMethod.PUT, B, path, null), true, 1);
    assertHelpful(call(HttpMethod.PUT, B, path, null), true, 1);
    assertThat(rows("COMMENT", comment)).isEqualTo(1L);
    assertThat(
            count(
                "SELECT day_count FROM subject.publish_quota WHERE user_id = ? AND kind = 'HELPFUL'",
                B))
        .isEqualTo(1L);
    assertThat(received(A)).isEqualTo(1L);

    assertHelpful(call(HttpMethod.DELETE, B, path, null), false, 0);
    assertThat(received(A)).isZero();
    assertHelpful(call(HttpMethod.DELETE, B, path, null), false, 0);
    assertHelpful(call(HttpMethod.DELETE, C, path, null), false, 0);
    assertThat(rows("COMMENT", comment)).isZero();
  }

  /// AC：给自己的评论、自己的匿名经验点都被拒（403）。
  @Test
  void should_reject_self_including_anonymous_post_author() {
    long comment = comment(A, "publication", PAPER, "根", null);
    long anonymousPost = experience(A, true);

    EntityExchangeResult<String> own =
        call(HttpMethod.PUT, A, "/comments/" + comment + "/helpful", null);
    EntityExchangeResult<String> ownAnonymous =
        call(HttpMethod.PUT, A, "/posts/" + anonymousPost + "/helpful", null);

    assertThat(status(own)).isEqualTo(403);
    assertThat(traits(own)).contains("SELF_HELPFUL");
    assertThat(status(ownAnonymous)).isEqualTo(403);
    assertThat(traits(ownAnonymous)).contains("SELF_HELPFUL");
    assertThat(count("SELECT count(*) FROM reaction.helpful")).isZero();
  }

  /// AC：邮箱未验证、冷静期账号被拒（409）；未登录 401。
  @Test
  void should_reject_ineligible_users() {
    long comment = comment(A, "publication", PAPER, "根", null);
    String path = "/comments/" + comment + "/helpful";

    EntityExchangeResult<String> unverified = call(HttpMethod.PUT, D, path, null);
    EntityExchangeResult<String> cooling = call(HttpMethod.PUT, E, path, null);

    assertThat(status(unverified)).isEqualTo(409);
    assertThat(traits(unverified)).contains("EMAIL_UNVERIFIED");
    assertThat(status(cooling)).isEqualTo(409);
    assertThat(traits(cooling)).contains("ACCOUNT_RESTRICTED");
    assertThat(status(call(HttpMethod.PUT, null, path, null))).isEqualTo(401);
    assertThat(count("SELECT count(*) FROM reaction.helpful")).isZero();
  }

  /// AC：今天已经点了 300 次，第 301 次 429、带 `Retry-After`，不落明细。
  @Test
  void should_reject_three_hundred_first_mark() {
    long comment = comment(A, "publication", PAPER, "根", null);
    jdbcTemplate.update(
        "INSERT INTO subject.publish_quota (user_id, kind, quota_day, day_count, last_published_at)"
            + " VALUES (?, 'HELPFUL', ?, 300, now())",
        B,
        today);

    HttpResponse<String> rejected;
    try (HttpClient client = HttpClient.newHttpClient()) {
      rejected = sendWithoutRetry(client, B, "/comments/" + comment + "/helpful");
    }

    assertThat(rejected.statusCode()).isEqualTo(429);
    assertThat(rejected.headers().firstValue("Retry-After")).isPresent();
    assertThat(rejected.body()).contains("DAILY_LIMIT_REACHED");
    assertThat(rows("COMMENT", comment)).isZero();
  }

  /// AC：匿名经验、作者在自己匿名经验下的评论收到的有帮助计入目标数、不计入获赞；同一经验下别人的评论照常计入。
  @Test
  void should_not_credit_anonymous_post_or_author_comment_under_it() {
    long post = experience(A, true);
    long ownerComment = comment(A, "post", post, "作者补充", null);
    long otherComment = comment(C, "post", post, "我也投过", null);

    assertHelpful(call(HttpMethod.PUT, B, "/posts/" + post + "/helpful", null), true, 1);
    assertHelpful(call(HttpMethod.PUT, B, "/comments/" + ownerComment + "/helpful", null), true, 1);
    assertHelpful(call(HttpMethod.PUT, B, "/comments/" + otherComment + "/helpful", null), true, 1);

    assertThat(received(A)).isZero();
    assertThat(received(C)).isEqualTo(1L);
  }

  /// 占位、整条去掉的、不存在的评论，已删除的经验与它下面的评论：都不能点（404）。
  @Test
  void should_reject_placeholder_and_deleted_targets() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(C, "publication", PAPER, "回复", root);
    long removed = comment(A, "publication", PAPER, "另一条", null);
    long post = experience(A, false);
    long underPost = comment(C, "post", post, "经验下的评论", null);
    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + root, null))).isEqualTo(200);
    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + removed, null))).isEqualTo(200);
    assertThat(status(call(HttpMethod.DELETE, A, "/posts/" + post, null))).isEqualTo(204);

    for (String path :
        List.of(
            "/comments/" + root + "/helpful",
            "/comments/" + removed + "/helpful",
            "/comments/987654321/helpful",
            "/posts/" + post + "/helpful",
            "/comments/" + underPost + "/helpful")) {
      EntityExchangeResult<String> result = call(HttpMethod.PUT, B, path, null);
      assertThat(status(result)).as(path).isEqualTo(404);
      assertThat(traits(result)).as(path).contains("HELPFUL_TARGET_NOT_FOUND");
    }
  }

  /// AC：评论整条去掉、变成占位，经验删除后，它收到的有帮助被删、获赞扣回；占位下的回复、经验下的评论收到的保留。
  /// 之后再取消对已删目标的有帮助照样成功。
  @Test
  void should_remove_helpful_with_deleted_targets() {
    long single = comment(A, "publication", PAPER, "没有回复", null);
    long parent = comment(A, "publication", PAPER, "有回复", null);
    long reply = comment(C, "publication", PAPER, "回复", parent);
    long post = experience(A, false);
    long underPost = comment(C, "post", post, "经验下的评论", null);
    for (long reader : List.of(B, C)) {
      call(HttpMethod.PUT, reader, "/comments/" + single + "/helpful", null);
      call(HttpMethod.PUT, reader, "/posts/" + post + "/helpful", null);
    }
    call(HttpMethod.PUT, B, "/comments/" + parent + "/helpful", null);
    call(HttpMethod.PUT, B, "/comments/" + reply + "/helpful", null);
    call(HttpMethod.PUT, B, "/comments/" + underPost + "/helpful", null);
    assertThat(received(A)).isEqualTo(5L);
    assertThat(received(C)).isEqualTo(2L);

    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + single, null))).isEqualTo(200);
    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + parent, null))).isEqualTo(200);
    assertThat(status(call(HttpMethod.DELETE, A, "/posts/" + post, null))).isEqualTo(204);

    for (long removed : List.of(single, parent)) {
      assertThat(rows("COMMENT", removed)).isZero();
      assertThat(
              count(
                  "SELECT count(*) FROM reaction.target_stats WHERE target_type = 'COMMENT'"
                      + " AND target_id = ?",
                  removed))
          .isZero();
    }
    assertThat(rows("POST", post)).isZero();
    assertThat(received(A)).isZero();
    assertThat(targetCount("COMMENT", reply)).isEqualTo(1L);
    assertThat(targetCount("COMMENT", underPost)).isEqualTo(1L);
    assertThat(received(C)).isEqualTo(2L);

    // 取消已删除的目标：明细已随删除清掉，照样 200、什么都不改
    assertHelpful(call(HttpMethod.DELETE, B, "/comments/" + single + "/helpful", null), false, 0);
    assertHelpful(call(HttpMethod.DELETE, B, "/posts/" + post + "/helpful", null), false, 0);
    assertThat(received(A)).isZero();
  }

  /// 删除事件的任一订阅方失败时，评论与经验的删除回滚，有帮助的明细、计数、获赞原样。状态码随订阅方抛的异常而定，
  /// 这里不关心。「联动与删除同事务」由下一条（有帮助自己的清理失败）证明。
  @Test
  void should_roll_back_delete_when_subscriber_fails() {
    long comment = comment(A, "publication", PAPER, "根", null);
    long post = experience(A, false);
    call(HttpMethod.PUT, B, "/comments/" + comment + "/helpful", null);
    call(HttpMethod.PUT, B, "/posts/" + post + "/helpful", null);
    DiscussionEventRecorder.failOnDelete = true;

    assertThat(status(call(HttpMethod.DELETE, A, "/comments/" + comment, null))).isNotEqualTo(200);
    assertThat(status(call(HttpMethod.DELETE, A, "/posts/" + post, null))).isNotEqualTo(204);

    assertThat(
            count(
                "SELECT count(*) FROM discussion.comment WHERE comment_id = ? AND state = 'ACTIVE'",
                comment))
        .isEqualTo(1L);
    assertThat(
            count("SELECT count(*) FROM post.post WHERE post_id = ? AND status = 'ACTIVE'", post))
        .isEqualTo(1L);
    assertThat(rows("COMMENT", comment)).isEqualTo(1L);
    assertThat(targetCount("COMMENT", comment)).isEqualTo(1L);
    assertThat(rows("POST", post)).isEqualTo(1L);
    assertThat(targetCount("POST", post)).isEqualTo(1L);
    assertThat(received(A)).isEqualTo(2L);
  }

  /// AC：有帮助的清理本身失败时（测试里用触发器让删明细报错），评论删除一起回滚、回 500：证明删除联动跑在删除的
  /// 同一事务里（改成提交后才执行的监听，评论会被删掉，这条就红）。
  @Test
  void should_roll_back_comment_delete_when_helpful_cleanup_fails() {
    long comment = comment(A, "publication", PAPER, "根", null);
    call(HttpMethod.PUT, B, "/comments/" + comment + "/helpful", null);
    jdbcTemplate.execute(
        "CREATE FUNCTION reaction.it_refuse_delete() RETURNS trigger AS $$"
            + " BEGIN RAISE EXCEPTION 'refused by test'; END; $$ LANGUAGE plpgsql");
    jdbcTemplate.execute(
        "CREATE TRIGGER it_refuse_delete BEFORE DELETE ON reaction.helpful"
            + " FOR EACH ROW EXECUTE FUNCTION reaction.it_refuse_delete()");
    EntityExchangeResult<String> result;
    try {
      result = call(HttpMethod.DELETE, A, "/comments/" + comment, null);
    } finally {
      jdbcTemplate.execute("DROP TRIGGER it_refuse_delete ON reaction.helpful");
      jdbcTemplate.execute("DROP FUNCTION reaction.it_refuse_delete()");
    }

    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(500);
    assertThat(
            count(
                "SELECT count(*) FROM discussion.comment WHERE comment_id = ? AND state = 'ACTIVE'",
                comment))
        .isEqualTo(1L);
    assertThat(rows("COMMENT", comment)).isEqualTo(1L);
    assertThat(targetCount("COMMENT", comment)).isEqualTo(1L);
    assertThat(received(A)).isEqualTo(1L);
  }

  /// spec §4.3 的竞态：删评论的事务已经删完明细、还没提交时，另一个人来点。用提交时才触发的约束触发器把删除事务停在
  /// 提交前（触发器去拿测试手里的一把锁），点的请求必须在目标锁上等删除提交、锁内重读后 404；不拿目标锁的实现会
  /// 读到还没提交的「正常」评论并插入，删除提交后留下孤儿明细、获赞多 1。
  @Test
  void should_not_leave_orphan_when_marking_during_comment_delete() throws Exception {
    long comment = comment(A, "publication", PAPER, "根", null);
    call(HttpMethod.PUT, C, "/comments/" + comment + "/helpful", null);
    jdbcTemplate.execute(
        "CREATE FUNCTION reaction.it_pause_commit() RETURNS trigger AS $$ BEGIN"
            + " PERFORM pg_advisory_xact_lock(hashtextextended('it.pause', 0)); RETURN NULL;"
            + " END; $$ LANGUAGE plpgsql");
    jdbcTemplate.execute(
        "CREATE CONSTRAINT TRIGGER it_pause_commit AFTER DELETE ON reaction.helpful"
            + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW"
            + " EXECUTE FUNCTION reaction.it_pause_commit()");
    // 连接最后声明、最先关闭：断言失败时先放开测试手里的锁，停住的删除事务才能结束，线程池才关得掉
    try (ExecutorService pool = Executors.newFixedThreadPool(2);
        HttpClient client = HttpClient.newHttpClient();
        Connection gate = dataSource.getConnection()) {
      advisory(gate, "pg_advisory_lock");
      CompletableFuture<HttpResponse<String>> delete =
          CompletableFuture.supplyAsync(
              () -> sendWithoutRetry(client, "DELETE", A, "/comments/" + comment), pool);
      awaitLockWaiters(1);
      CompletableFuture<HttpResponse<String>> mark =
          CompletableFuture.supplyAsync(
              () -> sendWithoutRetry(client, B, "/comments/" + comment + "/helpful"), pool);
      awaitMarkSettled(mark);
      advisory(gate, "pg_advisory_unlock");

      assertThat(delete.get(30, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      assertThat(mark.get(30, TimeUnit.SECONDS).statusCode()).isEqualTo(404);
    } finally {
      jdbcTemplate.execute("DROP TRIGGER it_pause_commit ON reaction.helpful");
      jdbcTemplate.execute("DROP FUNCTION reaction.it_pause_commit()");
    }

    assertThat(rows("COMMENT", comment)).isZero();
    assertThat(targetCount("COMMENT", comment)).isZero();
    assertThat(received(A)).isZero();
  }

  /// 在测试自己的连接上拿 / 放 `it.pause` 这把会话级锁。
  ///
  /// @param connection 连接
  /// @param function `pg_advisory_lock` 或 `pg_advisory_unlock`
  /// @throws SQLException 执行失败
  private static void advisory(Connection connection, String function) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT " + function + "(hashtextextended('it.pause', 0))")) {
      statement.execute();
    }
  }

  /// 等到至少有这么多会话在等锁。
  ///
  /// @param expected 等待者个数
  /// @throws InterruptedException 被中断
  private void awaitLockWaiters(int expected) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      if (lockWaiters() >= expected) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("没有等到 " + expected + " 个锁上的等待");
  }

  /// 等点的请求要么已经结束，要么也排在了锁上（正确的实现在目标锁上等；不拿目标锁的实现会在删除事务锁住的统计行上
  /// 等）。
  ///
  /// @param mark 点的请求
  /// @throws InterruptedException 被中断
  private void awaitMarkSettled(CompletableFuture<?> mark) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      if (mark.isDone() || lockWaiters() >= 2) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("点的请求既没结束也没在锁上等");
  }

  /// 在锁上等待的会话数。
  ///
  /// @return 个数
  private long lockWaiters() {
    return count("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'");
  }

  /// 发一条评论或回复，回它的 ID。先清掉作者的发表计数，绕开 10 秒间隔。
  ///
  /// @param user 作者
  /// @param type 对象类型
  /// @param key 对象 key
  /// @param body 正文
  /// @param parentId 父评论，根评论为 `null`
  /// @return 评论 ID
  private long comment(long user, String type, long key, String body, Long parentId) {
    jdbcTemplate.update(
        "DELETE FROM subject.publish_quota WHERE user_id = ? AND kind = 'COMMENT'", user);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("body", body);
    request.put("parentId", parentId == null ? null : parentId.toString());
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            user,
            "/discussions/" + type + "/" + key + "/comments",
            JSON.writeValueAsString(request));
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return Long.parseLong(json(result).get("commentId").asString());
  }

  /// 在期刊下写一条投稿经验，回它的 ID。每条换一个投稿日期。
  ///
  /// @param user 作者
  /// @param anonymous 是否匿名
  /// @return 帖子 ID
  private long experience(long user, boolean anonymous) {
    LocalDate submitted = today.minusDays(nextExperienceDay++);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("submittedOn", submitted.toString());
    request.put("firstDecisionOn", submitted.plusDays(10).toString());
    request.put("firstDecision", "MAJOR_REVISION");
    request.put("finalOutcome", "IN_PROGRESS");
    request.put("articleType", "ORIGINAL_RESEARCH");
    request.put("anonymous", anonymous);
    EntityExchangeResult<String> result =
        call(
            HttpMethod.POST,
            user,
            "/journals/" + JOURNAL + "/posts",
            JSON.writeValueAsString(request));
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(201);
    return Long.parseLong(json(result).get("postId").asString());
  }

  /// 断言点 / 取消的响应。
  ///
  /// @param result 响应
  /// @param helpful 期望的「我点着」
  /// @param count 期望的计数
  private static void assertHelpful(
      EntityExchangeResult<String> result, boolean helpful, int count) {
    assertThat(status(result)).as(result.getResponseBody()).isEqualTo(200);
    JsonNode body = json(result);
    assertThat(body.get("helpful").asBoolean()).isEqualTo(helpful);
    assertThat(body.get("helpfulCount").asInt()).isEqualTo(count);
  }

  /// 响应里的原因码。
  ///
  /// @param result 响应
  /// @return 原因码
  private static List<String> traits(EntityExchangeResult<String> result) {
    return json(result).get("traits").valueStream().map(JsonNode::asString).toList();
  }

  /// 目标的明细条数。
  ///
  /// @param type 种类
  /// @param id 目标 ID
  /// @return 条数
  private long rows(String type, long id) {
    return count(
        "SELECT count(*) FROM reaction.helpful WHERE target_type = ? AND target_id = ?", type, id);
  }

  /// 目标的有帮助数（没有统计行为 0）。
  ///
  /// @param type 种类
  /// @param id 目标 ID
  /// @return 计数
  private long targetCount(String type, long id) {
    return count(
        "SELECT COALESCE(sum(helpful_count), 0) FROM reaction.target_stats"
            + " WHERE target_type = ? AND target_id = ?",
        type,
        id);
  }

  /// 用户的获赞（没有统计行为 0）。
  ///
  /// @param userId 用户
  /// @return 获赞
  private long received(long userId) {
    return count(
        "SELECT COALESCE(sum(helpful_received), 0) FROM reaction.user_stats WHERE user_id = ?",
        userId);
  }

  /// 不经 `RestTestClient` 发 `PUT`：它遇到 429 会按 `Retry-After`（到明天 0 点）自动重发；并发测试也用它。
  ///
  /// @param client HTTP 客户端
  /// @param user 用户
  /// @param path 路径
  /// @return 响应
  private HttpResponse<String> sendWithoutRetry(HttpClient client, long user, String path) {
    return sendWithoutRetry(client, "PUT", user, path);
  }

  /// 不经 `RestTestClient` 发一个不带请求体的请求（并发测试里各线程各发各的）。
  ///
  /// @param client HTTP 客户端
  /// @param method 方法
  /// @param user 用户
  /// @param path 路径
  /// @return 响应
  private HttpResponse<String> sendWithoutRetry(
      HttpClient client, String method, long user, String path) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody());
    TestIdentity.headers(user)
        .forEach((name, values) -> values.forEach(v -> request.header(name, v)));
    try {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /// 等发令。
  ///
  /// @param start 发令
  private static void await(CountDownLatch start) {
    try {
      start.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
