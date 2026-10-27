package dev.patra.interaction.notification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.tool.security.test.TestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import tools.jackson.databind.JsonNode;

/// 提醒接口（AC 3、5）：未读数 304、单条与全部已读、匿名遮蔽、对象删除后不给摘录、分页、参数与登录。
class NotificationReadFlowIT extends NotificationFlowITSupport {

  private static final long A = 1001L;
  private static final long B = 1002L;
  private static final long OWNER = 770001L;
  private static final long PAPER = 100L;
  private static final long POST = 5001L;

  @BeforeEach
  void world() {
    publication(PAPER);
    users(A, B, OWNER);
  }

  /// 未读数没变化：304，缓存头允许浏览器留着条目；标已读后带旧 ETag 回 200。
  @Test
  void should_return_304_when_unread_count_unchanged() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(B, "publication", PAPER, "回复", root);

    EntityExchangeResult<String> first =
        call(HttpMethod.GET, A, "/me/notifications/unread-count", null);
    assertThat(status(first)).isEqualTo(200);
    String etag = first.getResponseHeaders().getETag();
    assertThat(etag).isNotBlank();

    EntityExchangeResult<String> again = conditional(A, etag);
    assertThat(status(again)).isEqualTo(304);
    assertThat(again.getResponseHeaders().getETag()).isEqualTo(etag);
    String cacheControl = again.getResponseHeaders().getCacheControl();
    assertThat(cacheControl).contains("no-cache").contains("private").doesNotContain("no-store");

    call(HttpMethod.PUT, A, "/me/notifications/read", null);
    EntityExchangeResult<String> changed = conditional(A, etag);
    assertThat(status(changed)).isEqualTo(200);
    assertThat(json(changed).get("total").asInt()).isZero();
  }

  /// 单条已读与全部已读返回新的未读数；别人的提醒 404。
  @Test
  void should_mark_one_and_all_read() {
    long root = comment(A, "publication", PAPER, "根", null);
    comment(B, "publication", PAPER, "回复一", root);
    comment(B, "publication", PAPER, "回复二", root);
    comment(B, "publication", PAPER, "@user-1001 看这里", null);
    long id =
        Long.parseLong(inbox(A, "replies").get("items").get(0).get("notificationId").asString());

    JsonNode one = json(call(HttpMethod.PUT, A, "/me/notifications/" + id + "/read", null));
    assertThat(one.get("total").asInt()).isEqualTo(2);
    assertThat(one.get("replies").asInt()).isEqualTo(1);
    assertThat(one.get("mentions").asInt()).isEqualTo(1);

    JsonNode all = json(call(HttpMethod.PUT, A, "/me/notifications/read", null));
    assertThat(all.get("total").asInt()).isZero();

    EntityExchangeResult<String> others =
        call(HttpMethod.PUT, B, "/me/notifications/" + id + "/read", null);
    assertThat(status(others)).isEqualTo(404);
    assertThat(json(others).get("traits").valueStream().map(JsonNode::asString).toList())
        .contains("NOTIFICATION_NOT_FOUND");
  }

  /// 匿名所有者回复 B：B 的提醒里没有触发人，响应原文里没有他的用户 ID。
  @Test
  void should_hide_anonymous_owner_id_from_recipient() {
    registerPost(POST, OWNER, true);
    long root = comment(B, "post", POST, "请问审稿多久", null);
    comment(OWNER, "post", POST, "六周左右", root);

    EntityExchangeResult<String> result = call(HttpMethod.GET, B, "/me/notifications", null);
    JsonNode item = json(result).get("items").get(0);
    assertThat(item.get("actor").isNull()).isTrue();
    assertThat(item.get("actorAnonymous").asBoolean()).isTrue();
    assertThat(item.get("subject").get("mine").asBoolean()).isFalse();
    assertThat(item.get("subject").get("abbreviation").asString()).isEqualTo("J Tests");
    assertThat(result.getResponseBody()).doesNotContain(Long.toString(OWNER));
  }

  /// 经验删除后：对象不可用，提醒里不给摘录。
  @Test
  void should_hide_excerpt_after_post_deleted() {
    registerPost(POST, OWNER, false);
    comment(B, "post", POST, "有个问题", null);
    deletePost(POST);

    JsonNode item = inbox(OWNER, "replies").get("items").get(0);
    assertThat(item.get("subject").get("available").asBoolean()).isFalse();
    assertThat(item.get("subject").get("mine").asBoolean()).isTrue();
    assertThat(item.get("excerpt").isNull()).isTrue();
  }

  /// 21 条：第一页 20 条带游标，第二页 1 条没有游标。
  @Test
  void should_page_twenty_per_tab() {
    long root = comment(A, "publication", PAPER, "根", null);
    for (int i = 0; i < 21; i++) {
      comment(B, "publication", PAPER, "回复 " + i, root);
    }

    JsonNode first = inbox(A, "replies");
    assertThat(first.get("items")).hasSize(20);
    String cursor = first.get("nextCursor").asString();
    JsonNode second =
        json(call(HttpMethod.GET, A, "/me/notifications?tab=replies&cursor=" + cursor, null));
    assertThat(second.get("items")).hasSize(1);
    assertThat(second.get("nextCursor").isNull()).isTrue();
  }

  /// 标签、游标不合法 422；路径里的提醒 ID 不是数字 400（Spring 类型转换，同 collection）；未登录 401。
  @Test
  void should_reject_bad_tab_and_require_login() {
    assertThat(status(call(HttpMethod.GET, A, "/me/notifications?tab=all", null))).isEqualTo(422);
    assertThat(status(call(HttpMethod.GET, A, "/me/notifications?cursor=abc", null)))
        .isEqualTo(422);
    assertThat(status(call(HttpMethod.PUT, A, "/me/notifications/abc/read", null))).isEqualTo(400);
    assertThat(status(call(HttpMethod.GET, null, "/me/notifications", null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.GET, null, "/me/notifications/unread-count", null)))
        .isEqualTo(401);
  }

  /// 带 `If-None-Match` 取未读数。
  ///
  /// @param user 收件人
  /// @param etag 上次的 ETag
  /// @return 响应
  private EntityExchangeResult<String> conditional(long user, String etag) {
    return restClient
        .get()
        .uri("/me/notifications/unread-count")
        .headers(h -> h.addAll(TestIdentity.headers(user)))
        .header(HttpHeaders.IF_NONE_MATCH, etag)
        .exchange()
        .expectBody(String.class)
        .returnResult();
  }
}
