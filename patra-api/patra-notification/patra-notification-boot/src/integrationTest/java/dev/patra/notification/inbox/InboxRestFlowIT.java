package dev.patra.notification.inbox;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.support.NotificationFlowITSupport;
import dev.patra.tool.security.test.TestIdentity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 门户的提醒接口（spec 第 8 节）：真实 PG、真实验签，identity 与 interaction 由 WireMock 扮演。
class InboxRestFlowIT extends NotificationFlowITSupport {

  private static final long ME = 2L;
  private static final long ACTOR = 9L;
  private static final String UNREAD = "/me/notifications/unread-count";

  /// 未登录 401。
  @Test
  void should_require_login() {
    assertThat(status(call(HttpMethod.GET, null, UNREAD, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.GET, null, "/me/notifications", null))).isEqualTo(401);
  }

  /// 未读数：三个数与铃铛数；ETag 覆盖三个；带上 ETag 再拉 304、不带体；两种响应都带 ETag 与只给本人的 no-cache。
  @Test
  void should_return_counts_with_etag_and_304() {
    insert(11L, ME, "REPLY", "COMMENT_REPLY", 500L, null);
    insert(12L, ME, "REPLY", "POST_COMMENT", 501L, null);
    insert(13L, ME, "MENTION", "MENTION", 502L, null);
    insert(14L, ME, "MENTION", "MENTION", 503L, "2026-10-31T09:00:00Z");

    EntityExchangeResult<String> first = call(HttpMethod.GET, ME, UNREAD, null);

    assertThat(status(first)).isEqualTo(200);
    JsonNode counts = json(first);
    assertThat(counts.get("total").asInt()).isEqualTo(3);
    assertThat(counts.get("replies").asInt()).isEqualTo(2);
    assertThat(counts.get("mentions").asInt()).isEqualTo(1);
    assertThat(counts.get("helpful").asInt()).isZero();
    assertThat(first.getResponseHeaders().getETag()).isEqualTo("\"2-1-0\"");
    assertThat(first.getResponseHeaders().getCacheControl()).contains("no-cache", "private");

    EntityExchangeResult<String> second = conditional("W/\"2-1-0\", \"x\"");
    assertThat(status(second)).isEqualTo(304);
    assertThat(second.getResponseBody()).isNull();
    assertThat(second.getResponseHeaders().getETag()).isEqualTo("\"2-1-0\"");
    assertThat(second.getResponseHeaders().getCacheControl())
        .contains("no-cache", "private")
        .doesNotContain("no-store");
    assertThat(status(conditional("*"))).isEqualTo(304);
    assertThat(status(conditional("\"1-1-0\""))).isEqualTo(200);
  }

  /// 拉未读数记在站（决策 H），命中 304 的也记。
  @Test
  void should_record_presence_on_poll_and_304() {
    call(HttpMethod.GET, ME, UNREAD, null);
    jdbcTemplate.update("DELETE FROM kernel.presence");

    assertThat(status(conditional("\"0-0-0\""))).isEqualTo(304);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM kernel.presence WHERE user_id = ?", Long.class, ME))
        .isEqualTo(1);
  }

  /// 列表：对象视图与经验结果读时现查、名片带访客、摘录换名；ID 是字符串。
  @Test
  void should_list_with_subject_from_interaction() {
    insert(11L, ME, "REPLY", "COMMENT_REPLY", 500L, null);
    jdbcTemplate.update(
        "INSERT INTO inbox.comment (comment_id, subject_id, version, plain_text, updated_at)"
            + " VALUES (500, 40, 1, '谢谢 <@4>', now())");
    stubSubjectViews(
        "{\"subjectId\":40,\"type\":\"POST\",\"key\":\"55\",\"available\":true,"
            + "\"journal\":\"The Lancet\",\"abbreviation\":\"Lancet\",\"publishedDate\":\"2026-08\","
            + "\"ownerId\":2,\"ownerAnonymous\":false}");
    stubPostOutlines(
        "{\"postId\":55,\"firstDecision\":\"MAJOR_REVISION\",\"finalOutcome\":\"ACCEPTED\","
            + "\"firstRoundDays\":41,\"totalDays\":96}");
    stubSummaries(
        "{\"userId\":9,\"username\":\"wang\",\"displayName\":\"王\",\"avatarPalette\":1,"
            + "\"accountStatus\":\"ACTIVE\",\"emailVerified\":true,\"profileVisible\":true},"
            + "{\"userId\":4,\"username\":\"zhou\",\"displayName\":\"周敏\",\"avatarPalette\":2,"
            + "\"accountStatus\":\"ACTIVE\",\"emailVerified\":true,\"profileVisible\":false}");

    EntityExchangeResult<String> result = call(HttpMethod.GET, ME, "/me/notifications", null);

    assertThat(status(result)).isEqualTo(200);
    JsonNode page = json(result);
    assertThat(page.get("nextCursor").isNull()).isTrue();
    JsonNode item = page.get("items").get(0);
    assertThat(item.get("notificationId").asString()).isEqualTo("11");
    assertThat(item.get("type").asString()).isEqualTo("COMMENT_REPLY");
    assertThat(item.get("read").asBoolean()).isFalse();
    assertThat(item.get("occurredAt").asString()).isEqualTo("2026-10-31T08:00:00Z");
    assertThat(item.get("actorCount").asInt()).isEqualTo(1);
    assertThat(item.get("commentId").asString()).isEqualTo("500");
    assertThat(item.get("excerpt").asString()).isEqualTo("谢谢 @周敏");
    assertThat(item.get("excerptTruncated").asBoolean()).isFalse();
    assertThat(item.get("actor").get("userId").asString()).isEqualTo("9");
    assertThat(item.get("actor").get("username").asString()).isEqualTo("wang");
    JsonNode subject = item.get("subject");
    assertThat(subject.get("type").asString()).isEqualTo("post");
    assertThat(subject.get("key").asString()).isEqualTo("55");
    assertThat(subject.get("submittedMonth").asString()).isEqualTo("2026-08");
    assertThat(subject.get("mine").asBoolean()).isTrue();
    assertThat(subject.get("experience").get("firstDecision").asString())
        .isEqualTo("MAJOR_REVISION");
    assertThat(subject.get("experience").get("totalDays").asInt()).isEqualTo(96);
    identity.verify(
        postRequestedFor(urlPathEqualTo("/_internal/users/summaries"))
            .withRequestBody(matchingJsonPath("$.viewerUserId", equalTo("2"))));
  }

  /// interaction 挂了：列表照常，对象与摘录为空。
  @Test
  void should_degrade_when_interaction_down() {
    insert(11L, ME, "REPLY", "COMMENT_REPLY", 500L, null);
    interactionDown();
    stubSummaries(
        "{\"userId\":9,\"username\":\"wang\",\"displayName\":\"王\",\"avatarPalette\":1,"
            + "\"accountStatus\":\"ACTIVE\",\"emailVerified\":true,\"profileVisible\":true}");

    EntityExchangeResult<String> result = call(HttpMethod.GET, ME, "/me/notifications", null);

    assertThat(status(result)).isEqualTo(200);
    JsonNode item = json(result).get("items").get(0);
    assertThat(item.get("subject").isNull()).isTrue();
    assertThat(item.get("excerpt").isNull()).isTrue();
  }

  /// 不认识的标签与游标 422。
  @Test
  void should_reject_bad_tab_and_cursor() {
    assertThat(status(call(HttpMethod.GET, ME, "/me/notifications?tab=likes", null)))
        .isEqualTo(422);
    assertThat(status(call(HttpMethod.GET, ME, "/me/notifications?cursor=abc!", null)))
        .isEqualTo(422);
  }

  /// 单条已读返回新的未读数；别人的 404；全部已读覆盖三个标签。
  @Test
  void should_mark_read_and_all_read() {
    insert(11L, ME, "REPLY", "COMMENT_REPLY", 500L, null);
    insert(12L, ME, "MENTION", "MENTION", 501L, null);
    insert(13L, 3L, "REPLY", "COMMENT_REPLY", 500L, null);

    EntityExchangeResult<String> one = call(HttpMethod.PUT, ME, "/me/notifications/11/read", null);
    assertThat(status(one)).isEqualTo(200);
    assertThat(json(one).get("replies").asInt()).isZero();
    assertThat(json(one).get("mentions").asInt()).isEqualTo(1);

    EntityExchangeResult<String> others =
        call(HttpMethod.PUT, ME, "/me/notifications/13/read", null);
    assertThat(status(others)).isEqualTo(404);
    assertThat(json(others).get("code").asString()).isEqualTo("NOTIFICATION-0404");

    EntityExchangeResult<String> all = call(HttpMethod.PUT, ME, "/me/notifications/read", null);
    assertThat(status(all)).isEqualTo(200);
    assertThat(json(all).get("total").asInt()).isZero();
  }

  /// 带 `If-None-Match` 拉未读数。
  ///
  /// @param ifNoneMatch 请求头的值
  /// @return 响应
  private EntityExchangeResult<String> conditional(String ifNoneMatch) {
    return restClient
        .get()
        .uri(UNREAD)
        .headers(h -> h.addAll(TestIdentity.headers(ME)))
        .header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch)
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }

  /// 直接插一行提醒：触发人 9、对象 40、排序时间 2026-10-31T08:00:00Z。
  ///
  /// @param id 提醒 ID
  /// @param recipient 收件人
  /// @param category 类别
  /// @param type 类型
  /// @param commentId 评论
  /// @param readAt 已读时刻，可空
  private void insert(
      long id, long recipient, String category, String type, long commentId, String readAt) {
    OffsetDateTime at = OffsetDateTime.parse("2026-10-31T08:00:00Z");
    jdbcTemplate.update(
        "INSERT INTO inbox.notification (recipient_id, notification_id, category, type, actor_id,"
            + " actor_anonymous, subject_id, comment_id, occurred_at, created_at, read_at)"
            + " VALUES (?, ?, ?, ?, ?, false, 40, ?, ?, ?, ?)",
        recipient,
        id,
        category,
        type,
        ACTOR,
        commentId,
        at,
        at,
        readAt == null ? null : OffsetDateTime.parse(readAt));
  }
}
