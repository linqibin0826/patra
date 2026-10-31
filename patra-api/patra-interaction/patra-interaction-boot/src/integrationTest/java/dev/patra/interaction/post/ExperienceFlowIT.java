package dev.patra.interaction.post;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.support.InteractionFlowITSupport;
import dev.patra.tool.security.test.TestIdentity;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 投稿经验的完整流程（PAP-122 的 AC），经 HTTP、真实 PG、假 catalog（期刊解析）与假 identity：
/// 汇总口径与窗口、匿名遮蔽（以他人身份取原始响应体）、删除、同日查重、资格与频率、登记表、修改历史。
class ExperienceFlowIT extends InteractionFlowITSupport {

  /// 作者 A：ID 取一个不会在别处出现的数，断言响应体不含它。
  private static final long A = 777000111L;

  /// 读者 B。
  private static final long B = 777000222L;

  /// 邮箱未验证的 C。
  private static final long C = 777000333L;

  private static final String JOURNAL = "100";
  private static final String POSTS = "/journals/" + JOURNAL + "/posts";

  @LocalServerPort private int port;

  private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

  @BeforeEach
  void world() {
    CATALOG_DATA.putJournal(100L, "0404401", "Journal 100", "J 100");
    IDENTITY_DATA.put(A, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.put(B, "ACTIVE", true, "PUBLIC");
    IDENTITY_DATA.put(C, "ACTIVE", false, "PUBLIC");
  }

  /// AC：写完经验后，下一次读期刊汇总立即反映。
  @Test
  void should_reflect_new_post_in_next_summary() {
    assertThat(json(get(null, POSTS + "/summary")).get("count").asInt()).isZero();

    assertThat(status(publish(A, revised(today.minusDays(60), 20, "ACCEPTED"), false)))
        .isEqualTo(201);

    JsonNode summary = json(get(null, POSTS + "/summary"));
    assertThat(summary.get("count").asInt()).isEqualTo(1);
    assertThat(summary.get("allCount").asInt()).isEqualTo(1);
    assertThat(summary.get("sufficient").asBoolean()).isFalse();
  }

  /// AC：满 5 条才出统计；近 3 年与全部切换；撤稿与进行中不计入录用率；分位数线性插值、半进位。
  @Test
  void should_compute_summary_by_window_and_threshold() {
    publish(A, revised(today.minusDays(200), 10, "ACCEPTED"), false);
    publish(A, revised(today.minusDays(201), 20, "REJECTED"), false);
    publish(A, revised(today.minusDays(202), 30, "WITHDRAWN"), false);
    publish(A, revised(today.minusDays(203), 40, "IN_PROGRESS"), false);
    JsonNode four = json(get(null, POSTS + "/summary"));
    assertThat(four.get("sufficient").asBoolean()).isFalse();
    assertThat(four.get("firstRound").isNull()).isTrue();

    publish(A, desk(today.minusDays(204), 3), false);
    publish(A, revised(today.minusYears(4), 50, "ACCEPTED"), false);

    JsonNode recent = json(get(null, POSTS + "/summary?window=3y"));
    assertThat(recent.get("window").asString()).isEqualTo("3y");
    assertThat(recent.get("count").asInt()).isEqualTo(5);
    assertThat(recent.get("allCount").asInt()).isEqualTo(6);
    assertThat(recent.get("sufficient").asBoolean()).isTrue();
    assertThat(numbers(recent.get("firstRound"), "median", "p25", "p75", "sampleSize"))
        .containsExactly(25, 18, 33, 4);
    assertThat(numbers(recent.get("deskReject"), "median", "sampleSize")).containsExactly(3, 1);
    assertThat(numbers(recent.get("outcomes"), "accepted", "rejected", "withdrawn", "inProgress"))
        .containsExactly(1, 2, 1, 1);
    assertThat(recent.get("acceptance").get("decidedCount").asInt()).isEqualTo(3);
    assertThat(recent.get("acceptance").get("rate").asDouble()).isEqualTo(1.0 / 3);

    JsonNode all = json(get(null, POSTS + "/summary?window=all"));
    assertThat(all.get("count").asInt()).isEqualTo(6);
    assertThat(numbers(all.get("firstRound"), "median", "p25", "p75", "sampleSize"))
        .containsExactly(30, 20, 40, 5);
    assertThat(all.get("acceptance").get("rate").asDouble()).isEqualTo(0.5);
    assertThat(status(get(null, POSTS + "/summary?window=5y"))).isEqualTo(422);
  }

  /// AC：他人视角的期刊列表、详情、修改历史、主页标签都拿不到作者 ID；本人视角可见并标注匿名。
  @Test
  void should_hide_anonymous_author_from_others() {
    String id = postId(publish(A, revised(today.minusDays(30), 10, "IN_PROGRESS"), true));
    backdate(id);
    assertThat(
            status(
                send(
                    HttpMethod.PUT,
                    A,
                    "/posts/" + id,
                    revised(today.minusDays(30), 12, "IN_PROGRESS"))))
        .isEqualTo(200);

    for (Long viewer : new Long[] {B, null}) {
      for (String uri :
          List.of(POSTS, "/posts/" + id, "/posts/" + id + "/revisions", "/users/" + A + "/posts")) {
        EntityExchangeResult<String> result = get(viewer, uri);
        assertThat(status(result)).as(uri).isEqualTo(200);
        assertThat(result.getResponseBody()).as(uri).doesNotContain(Long.toString(A));
      }
    }
    JsonNode forOther = json(get(B, "/posts/" + id)).get("experience");
    assertThat(forOther.get("author").isNull()).isTrue();
    assertThat(forOther.get("anonymous").asBoolean()).isTrue();
    assertThat(forOther.get("mine").asBoolean()).isFalse();
    assertThat(json(get(B, "/users/" + A + "/posts")).get("items")).isEmpty();

    JsonNode forSelf = json(get(A, "/posts/" + id)).get("experience");
    assertThat(forSelf.get("mine").asBoolean()).isTrue();
    assertThat(forSelf.get("anonymous").asBoolean()).isTrue();
    assertThat(forSelf.get("author").get("userId").asString()).isEqualTo(Long.toString(A));
    JsonNode own = json(get(A, "/users/" + A + "/posts")).get("items");
    assertThat(own).hasSize(1);
    assertThat(own.get(0).get("experience").get("anonymous").asBoolean()).isTrue();
    assertThat(own.get(0).get("journal").get("title").asString()).isEqualTo("Journal 100");
  }

  /// AC：删除后从列表与汇总中消失，详情返回「已删除」；登记行失效；再删一次照样 204。
  @Test
  void should_remove_deleted_post_from_list_and_summary() {
    String id = postId(publish(A, revised(today.minusDays(30), 10, "ACCEPTED"), false));

    assertThat(status(send(HttpMethod.DELETE, A, "/posts/" + id, null))).isEqualTo(204);

    assertThat(json(get(null, POSTS)).get("items")).isEmpty();
    assertThat(json(get(null, POSTS + "/summary")).get("count").asInt()).isZero();
    assertThat(json(get(A, "/users/" + A + "/posts")).get("items")).isEmpty();
    JsonNode detail = json(get(null, "/posts/" + id));
    assertThat(detail.get("deleted").asBoolean()).isTrue();
    assertThat(detail.get("experience").isNull()).isTrue();
    assertThat(detail.get("journal").get("key").asString()).isEqualTo(JOURNAL);
    EntityExchangeResult<String> revisions = get(null, "/posts/" + id + "/revisions");
    assertThat(status(revisions)).isEqualTo(404);
    assertThat(traits(revisions)).contains("SUBJECT_UNAVAILABLE");
    assertThat(registryState(id)).isEqualTo("UNAVAILABLE");
    assertThat(status(send(HttpMethod.DELETE, A, "/posts/" + id, null))).isEqualTo(204);
  }

  /// AC：同一人对同一刊用相同投稿日期重复创建被拒，响应带已有经验的 ID；删掉之后可以再写。
  @Test
  void should_reject_duplicate_submission_date() {
    LocalDate submitted = today.minusDays(40);
    String first = postId(publish(A, revised(submitted, 10, "IN_PROGRESS"), false));

    EntityExchangeResult<String> duplicate =
        publish(A, revised(submitted, 15, "IN_PROGRESS"), true);

    assertThat(status(duplicate)).isEqualTo(409);
    assertThat(traits(duplicate)).contains("DUPLICATE_SUBMISSION");
    assertThat(json(duplicate).get("existingPostId").asString()).isEqualTo(first);
    assertThat(json(duplicate).get("errors").get(0).get("field").asString())
        .isEqualTo("submittedOn");
    assertThat(status(publish(B, revised(submitted, 10, "IN_PROGRESS"), false))).isEqualTo(201);

    send(HttpMethod.DELETE, A, "/posts/" + first, null);
    assertThat(status(publish(A, revised(submitted, 15, "IN_PROGRESS"), false))).isEqualTo(201);
  }

  /// 同一作者同时发两条同一投稿日期的：只成功一条。
  @Test
  void should_allow_only_one_of_concurrent_same_date_posts() throws Exception {
    Map<String, Object> body = revised(today.minusDays(50), 10, "IN_PROGRESS");
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      CompletableFuture<Integer> first =
          CompletableFuture.supplyAsync(() -> status(publish(A, body, false)), pool);
      CompletableFuture<Integer> second =
          CompletableFuture.supplyAsync(() -> status(publish(A, body, false)), pool);

      assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
    }
    assertThat(count("SELECT count(*) FROM post.post WHERE author_user_id = ?", A)).isEqualTo(1);
  }

  /// 资格与频率：未登录 401；邮箱未验证 409；同一天第 11 条 429；被封禁后不能编辑、仍能删除。
  @Test
  void should_enforce_eligibility_and_daily_limit() throws Exception {
    assertThat(status(publish(null, revised(today.minusDays(10), 5, "IN_PROGRESS"), false)))
        .isEqualTo(401);
    EntityExchangeResult<String> unverified =
        publish(C, revised(today.minusDays(10), 5, "IN_PROGRESS"), false);
    assertThat(status(unverified)).isEqualTo(409);
    assertThat(traits(unverified)).contains("EMAIL_UNVERIFIED");

    for (int i = 0; i < 10; i++) {
      assertThat(status(publish(A, revised(today.minusDays(100 + i), 5, "IN_PROGRESS"), false)))
          .isEqualTo(201);
    }
    HttpResponse<String> eleventh =
        postWithoutRetry(A, revised(today.minusDays(200), 5, "IN_PROGRESS"));
    assertThat(eleventh.statusCode()).isEqualTo(429);
    assertThat(eleventh.headers().firstValue("Retry-After")).isPresent();
    assertThat(eleventh.body()).contains("DAILY_LIMIT_REACHED");

    String id = json(get(A, POSTS)).get("items").get(0).get("postId").asString();
    IDENTITY_DATA.put(A, "BANNED", true, "PUBLIC");
    EntityExchangeResult<String> edit =
        send(HttpMethod.PUT, A, "/posts/" + id, revised(today.minusDays(100), 6, "IN_PROGRESS"));
    assertThat(status(edit)).isEqualTo(409);
    assertThat(traits(edit)).contains("ACCOUNT_RESTRICTED");
    assertThat(status(send(HttpMethod.DELETE, A, "/posts/" + id, null))).isEqualTo(204);
  }

  /// 登记表：经验是 POST 对象，带所有者与匿名标记；快照是期刊全称、投稿月份、缩写；改投稿月份后快照随之变。
  @Test
  void should_register_post_subject_with_owner_and_snapshot() {
    LocalDate submitted = today.minusMonths(3).withDayOfMonth(1);
    String id = postId(publish(A, revised(submitted, 10, "IN_PROGRESS"), true));

    Map<String, Object> row = registryRow(id);
    assertThat(row).containsEntry("owner_user_id", A).containsEntry("owner_anonymous", true);
    assertThat((String) row.get("snapshot"))
        .contains("\"journal\": \"Journal 100\"")
        .contains("\"abbreviation\": \"J 100\"")
        .contains("\"publishedDate\": \"" + submitted.toString().substring(0, 7) + "\"");

    LocalDate moved = submitted.minusMonths(1);
    send(HttpMethod.PUT, A, "/posts/" + id, revised(moved, 40, "IN_PROGRESS"));
    assertThat((String) registryRow(id).get("snapshot"))
        .contains("\"publishedDate\": \"" + moved.toString().substring(0, 7) + "\"");
  }

  /// 内部接口（PAP-143，通知服务读时现查）：对象视图带 key、期刊快照、所有者；经验结果按帖子 ID 取，删除后仍可取。
  @Test
  void should_serve_subject_view_and_outline_internally() {
    LocalDate submitted = today.minusMonths(3).withDayOfMonth(1);
    String id = postId(publish(A, revised(submitted, 10, "IN_PROGRESS"), true));
    long subjectId =
        jdbcTemplate.queryForObject(
            "SELECT subject_id FROM subject.registry WHERE target_type = 3 AND source_id = ?",
            Long.class,
            Long.parseLong(id));

    EntityExchangeResult<String> views =
        call(
            HttpMethod.POST,
            null,
            "/_internal/subjects/views",
            "{\"subjectIds\":[" + subjectId + ", 999999]}");
    send(HttpMethod.DELETE, A, "/posts/" + id, null);
    EntityExchangeResult<String> outlines =
        call(HttpMethod.POST, null, "/_internal/posts/outlines", "{\"postIds\":[" + id + "]}");

    assertThat(status(views)).isEqualTo(200);
    JsonNode view = json(views).get("subjects").get(0);
    assertThat(json(views).get("subjects")).hasSize(1);
    assertThat(view.get("type").asString()).isEqualTo("POST");
    assertThat(view.get("key").asString()).isEqualTo(id);
    assertThat(view.get("available").asBoolean()).isTrue();
    assertThat(view.get("journal").asString()).isEqualTo("Journal 100");
    assertThat(view.get("publishedDate").asString())
        .isEqualTo(submitted.toString().substring(0, 7));
    assertThat(view.get("ownerId").asLong()).isEqualTo(A);
    assertThat(view.get("ownerAnonymous").asBoolean()).isTrue();
    assertThat(status(outlines)).isEqualTo(200);
    JsonNode outline = json(outlines).get("outlines").get(0);
    assertThat(outline.get("firstDecision").asString()).isEqualTo("MAJOR_REVISION");
    assertThat(outline.get("finalOutcome").asString()).isEqualTo("IN_PROGRESS");
    assertThat(outline.get("firstRoundDays").asInt()).isEqualTo(10);
    assertThat(outline.get("totalDays").isNull()).isTrue();
    assertThat(
            status(
                call(
                    HttpMethod.POST, null, "/_internal/posts/outlines", "{\"postIds\":[1, null]}")))
        .isEqualTo(422);
  }

  /// 修改历史：发布后马上改不记版本、不算「已编辑」；过了 5 分钟再改记一版，当前版本在前。
  @Test
  void should_record_revision_only_after_grace() {
    LocalDate submitted = today.minusDays(30);
    String id = postId(publish(A, revised(submitted, 10, "IN_PROGRESS"), false));

    JsonNode quick =
        json(
            send(
                HttpMethod.PUT,
                A,
                "/posts/" + id,
                withBody(revised(submitted, 10, "IN_PROGRESS"), "补一句")));
    assertThat(quick.get("experience").get("edited").asBoolean()).isFalse();
    assertThat(json(get(null, "/posts/" + id + "/revisions")).get("items")).hasSize(1);

    backdate(id);
    JsonNode later =
        json(
            send(
                HttpMethod.PUT,
                A,
                "/posts/" + id,
                withBody(revised(submitted, 10, "IN_PROGRESS"), "再补一句")));
    assertThat(later.get("experience").get("edited").asBoolean()).isTrue();
    JsonNode versions = json(get(null, "/posts/" + id + "/revisions")).get("items");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).get("current").asBoolean()).isTrue();
    assertThat(versions.get(0).get("body").asString()).isEqualTo("再补一句");
    assertThat(versions.get(1).get("body").asString()).isEqualTo("补一句");
    assertThat(versions.get(1).get("firstDecision").asString()).isEqualTo("MAJOR_REVISION");
  }

  /// 不是作者编辑、删除：403；不存在：404。
  @Test
  void should_reject_foreign_edit_and_unknown_post() {
    String id = postId(publish(A, revised(today.minusDays(30), 10, "IN_PROGRESS"), false));

    assertThat(
            status(
                send(
                    HttpMethod.PUT,
                    B,
                    "/posts/" + id,
                    revised(today.minusDays(30), 11, "IN_PROGRESS"))))
        .isEqualTo(403);
    assertThat(status(send(HttpMethod.DELETE, B, "/posts/" + id, null))).isEqualTo(403);
    assertThat(status(get(null, "/posts/123"))).isEqualTo(404);
    assertThat(
            status(
                send(
                    HttpMethod.PUT,
                    A,
                    "/posts/123",
                    revised(today.minusDays(30), 11, "IN_PROGRESS"))))
        .isEqualTo(404);
  }

  /// 字段错误一次列出（422）；没登记过的期刊列表为空；期刊不存在 404。
  @Test
  void should_validate_fields_and_journal() {
    Map<String, Object> body = revised(today.minusDays(30), 10, "IN_PROGRESS");
    body.remove("submittedOn");
    body.put("firstDecision", "DESK_REJECT");
    body.put("finalOutcome", "ACCEPTED");

    EntityExchangeResult<String> invalid = publish(A, body, false);

    assertThat(status(invalid)).isEqualTo(422);
    assertThat(
            StreamSupport.stream(json(invalid).get("errors").spliterator(), false)
                .map(e -> e.get("field").asString() + "/" + e.get("code").asString())
                .toList())
        .containsExactly("submittedOn/REQUIRED", "finalOutcome/INCONSISTENT");
    assertThat(json(get(null, "/journals/200/posts")).get("items")).isEmpty();
    assertThat(
            status(
                send(
                    HttpMethod.POST,
                    A,
                    "/journals/200/posts",
                    revised(today.minusDays(30), 10, "IN_PROGRESS"))))
        .isEqualTo(404);
  }

  /// PAP-132 AC：经验列表两种排序都对，不传参数时与现有顺序一致；两种都带总条数；游标跟着排序走。
  @Test
  void should_sort_journal_experiences_by_helpful() {
    long d = 777000444L;
    IDENTITY_DATA.put(d, "ACTIVE", true, "PUBLIC");
    String e1 = postId(publish(A, revised(today.minusDays(30), 10, "IN_PROGRESS"), false));
    String e2 = postId(publish(A, revised(today.minusDays(10), 10, "IN_PROGRESS"), false));
    String e3 = postId(publish(A, revised(today.minusDays(20), 10, "IN_PROGRESS"), false));
    mark(B, e1);
    mark(d, e1);
    mark(B, e3);

    JsonNode latest = json(get(null, POSTS));
    assertThat(postIds(latest)).containsExactly(e2, e3, e1);
    assertThat(latest.get("total").asLong()).isEqualTo(3);
    assertThat(postIds(json(get(null, POSTS + "?sort=latest")))).containsExactly(e2, e3, e1);

    JsonNode helpful = json(get(null, POSTS + "?sort=helpful"));
    assertThat(postIds(helpful)).containsExactly(e1, e3, e2);
    assertThat(helpful.get("total").asLong()).isEqualTo(3);
    assertThat(helpful.get("items").get(0).get("helpfulCount").asInt()).isEqualTo(2);

    JsonNode first = json(get(null, POSTS + "?sort=helpful&limit=2"));
    String cursor = first.get("nextCursor").asString();
    assertThat(postIds(json(get(null, POSTS + "?sort=helpful&limit=2&cursor=" + cursor))))
        .containsExactly(e2);
    assertThat(status(get(null, POSTS + "?sort=latest&cursor=" + cursor))).isEqualTo(422);
    assertThat(status(get(null, POSTS + "?sort=hot"))).isEqualTo(422);
  }

  /// PAP-132 AC：期刊页列表、主页列表、经验页都带有帮助数、回复数（未删除评论）与「我点过」（只在有会话时）。
  @Test
  void should_carry_helpful_and_reply_count_on_experiences() {
    String post = postId(publish(A, revised(today.minusDays(30), 10, "IN_PROGRESS"), false));
    commentOn(B, post, "请问审稿人几位", null);
    String removed = commentOn(B, post, "补充一句", null);
    assertThat(status(call(HttpMethod.DELETE, B, "/comments/" + removed, null))).isEqualTo(200);
    mark(B, post);

    JsonNode forB = json(get(B, POSTS)).get("items").get(0);
    assertThat(forB.get("replyCount").asInt()).isEqualTo(1);
    assertThat(forB.get("helpfulCount").asInt()).isEqualTo(1);
    assertThat(forB.get("helpful").asBoolean()).isTrue();
    JsonNode forGuest = json(get(null, POSTS)).get("items").get(0);
    assertThat(forGuest.get("helpful").isNull()).isTrue();
    assertThat(forGuest.get("replyCount").asInt()).isEqualTo(1);

    JsonNode detail = json(get(B, "/posts/" + post)).get("experience");
    assertThat(detail.get("helpfulCount").asInt()).isEqualTo(1);
    assertThat(detail.get("replyCount").asInt()).isEqualTo(1);
    assertThat(detail.get("helpful").asBoolean()).isTrue();

    JsonNode profile = json(get(A, "/users/" + A + "/posts")).get("items").get(0);
    assertThat(profile.get("experience").get("replyCount").asInt()).isEqualTo(1);
    assertThat(profile.get("experience").get("helpful").asBoolean()).isFalse();
  }

  /// 给经验点有帮助，要求 200。
  ///
  /// @param user 点的人
  /// @param postId 经验
  private void mark(long user, String postId) {
    assertThat(status(call(HttpMethod.PUT, user, "/posts/" + postId + "/helpful", null)))
        .isEqualTo(200);
  }

  /// 一页里的经验 ID。
  ///
  /// @param page 列表页
  /// @return ID
  private static List<String> postIds(JsonNode page) {
    return page.get("items").valueStream().map(i -> i.get("postId").asString()).toList();
  }

  /// 首轮大修、给定用时与最终结果的一条（最终日期不填）。
  ///
  /// @param submitted 投稿日期
  /// @param firstDays 首轮用时
  /// @param outcome 最终结果
  /// @return 请求体
  private static Map<String, Object> revised(LocalDate submitted, int firstDays, String outcome) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("submittedOn", submitted.toString());
    body.put("firstDecisionOn", submitted.plusDays(firstDays).toString());
    body.put("firstDecision", "MAJOR_REVISION");
    body.put("finalOutcome", outcome);
    body.put("articleType", "ORIGINAL_RESEARCH");
    return body;
  }

  /// 直接拒稿、给定用时的一条。
  ///
  /// @param submitted 投稿日期
  /// @param days 用时
  /// @return 请求体
  private static Map<String, Object> desk(LocalDate submitted, int days) {
    Map<String, Object> body = revised(submitted, days, "REJECTED");
    body.put("firstDecision", "DESK_REJECT");
    return body;
  }

  /// 加上正文。
  ///
  /// @param body 请求体
  /// @param text 正文
  /// @return 请求体
  private static Map<String, Object> withBody(Map<String, Object> body, String text) {
    body.put("body", text);
    return body;
  }

  /// 写一条经验。
  ///
  /// @param user 作者，匿名访客为 `null`
  /// @param body 请求体
  /// @param anonymous 是否匿名
  /// @return 响应
  private EntityExchangeResult<String> publish(
      Long user, Map<String, Object> body, boolean anonymous) {
    Map<String, Object> request = new LinkedHashMap<>(body);
    request.put("anonymous", anonymous);
    return send(HttpMethod.POST, user, POSTS, request);
  }

  /// 在经验下发评论，要求 201，回评论 ID。先清掉发表计数，绕开 10 秒间隔。
  ///
  /// @param user 作者
  /// @param postId 经验 ID
  /// @param body 正文
  /// @param parentId 父评论，根评论为 `null`
  /// @return 评论 ID
  private String commentOn(long user, String postId, String body, String parentId) {
    jdbcTemplate.update("DELETE FROM subject.publish_quota WHERE user_id = ?", user);
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("body", body);
    request.put("parentId", parentId);
    EntityExchangeResult<String> result =
        send(HttpMethod.POST, user, "/discussions/post/" + postId + "/comments", request);
    assertThat(status(result)).isEqualTo(201);
    return json(result).get("commentId").asString();
  }

  /// 带 JSON 请求体的请求。
  ///
  /// @param method 方法
  /// @param user 用户，可空
  /// @param uri 路径
  /// @param body 请求体，可空
  /// @return 响应
  private EntityExchangeResult<String> send(
      HttpMethod method, Long user, String uri, Map<String, Object> body) {
    return call(method, user, uri, body == null ? null : JSON.writeValueAsString(body));
  }

  /// GET。
  ///
  /// @param user 用户，可空
  /// @param uri 路径
  /// @return 响应
  private EntityExchangeResult<String> get(Long user, String uri) {
    return call(HttpMethod.GET, user, uri, null);
  }

  /// 不经 `RestTestClient` 直接发：它底下的 Apache HttpClient 遇到 429 会按 `Retry-After`（到明天 0 点）自动重发、
  /// 测试一直卡住。
  ///
  /// @param user 作者
  /// @param body 请求体
  /// @return 响应
  /// @throws Exception 请求失败
  private HttpResponse<String> postWithoutRetry(long user, Map<String, Object> body)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + POSTS))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    TestIdentity.headers(user)
        .forEach((name, values) -> values.forEach(v -> request.header(name, v)));
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  /// 新经验的 ID。
  ///
  /// @param created 201 响应
  /// @return ID
  private static String postId(EntityExchangeResult<String> created) {
    assertThat(status(created)).isEqualTo(201);
    return json(created).get("postId").asString();
  }

  /// 响应的 `traits`。
  ///
  /// @param result 响应
  /// @return 原因码
  private static List<String> traits(EntityExchangeResult<String> result) {
    return StreamSupport.stream(json(result).get("traits").spliterator(), false)
        .map(JsonNode::asString)
        .toList();
  }

  /// 取对象里的若干整数。
  ///
  /// @param node 对象
  /// @param names 字段名
  /// @return 值
  private static List<Integer> numbers(JsonNode node, String... names) {
    return Arrays.stream(names).map(n -> node.get(n).asInt()).toList();
  }

  /// 把发布时刻拨回 10 分钟前（过了「5 分钟内不记版本」的宽限期）。
  ///
  /// @param id 经验 ID
  private void backdate(String id) {
    jdbcTemplate.update(
        "UPDATE post.post SET created_at = created_at - INTERVAL '10 minutes' WHERE post_id = ?",
        Long.parseLong(id));
  }

  /// 经验的登记行。
  ///
  /// @param id 经验 ID
  /// @return 所有者、匿名标记、快照、状态
  private Map<String, Object> registryRow(String id) {
    return jdbcTemplate.queryForMap(
        "SELECT owner_user_id, owner_anonymous, snapshot::text AS snapshot, state"
            + " FROM subject.registry WHERE target_type = 3 AND source_id = ?",
        Long.parseLong(id));
  }

  /// 经验登记行的状态。
  ///
  /// @param id 经验 ID
  /// @return 状态
  private String registryState(String id) {
    return (String) registryRow(id).get("state");
  }
}
