package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 批量用户摘要的完整流程（PAP-108 AC 1、2）：真实过滤器链、PostgreSQL、Redis。`profileVisible` 与公开主页
/// `GET /users/{username}` 的 404 / 200 逐格对照；状态用 SQL 改，请求者的断言用测试时钟签。
@DisplayName("批量用户摘要的完整流程")
class UserSummariesFlowIT extends IdentityFlowITSupport {

  private static final String SUMMARIES = "/_internal/users/summaries";

  @Test
  @DisplayName("100 个 ID（含重复与不存在的）按去重后的请求顺序返回，不存在的省略")
  void should_return_100_users_in_request_order() {
    long a = userIdOf(register("order.a@example.com", "order-a"));
    long b = userIdOf(register("order.b@example.com", "order-b"));
    long c = userIdOf(register("order.c@example.com", "order-c"));
    List<Long> ids = new ArrayList<>(List.of(c, a, 999_999L, b, a));
    for (long missing = 1_000_000L; ids.size() < 100; missing++) {
      ids.add(missing);
    }

    JsonNode users = summaries(ids, null);

    List<String> order = new ArrayList<>();
    users.forEach(user -> order.add(user.get("userId").asString()));
    assertThat(order).containsExactly(String.valueOf(c), String.valueOf(a), String.valueOf(b));
  }

  @Test
  @DisplayName("101 个 ID 回 422")
  void should_reject_101_ids() {
    List<Long> ids = new ArrayList<>();
    for (long id = 1; id <= 101; id++) {
      ids.add(id);
    }

    RawResponse response = post(SUMMARIES, Map.of("userIds", ids), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(422);
    assertThat(json(response).get("errors").get(0).get("code").asString()).isEqualTo("TOO_MANY");
  }

  @Test
  @DisplayName("已注销：占位，响应里不出现用户名")
  void should_not_leak_username_of_deleted_user() {
    long gone = userIdOf(register("gone.user@example.com", "gone-user"));
    markDeleted(gone);

    RawResponse response =
        post(SUMMARIES, Map.of("userIds", List.of(gone), "viewerUserId", gone), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(response.body()).doesNotContain("gone-user");
    JsonNode user = json(response).get("users").get(0);
    assertThat(user.get("accountStatus").asString()).isEqualTo("DELETED");
    assertThat(user.get("username").isNull()).isTrue();
    assertThat(user.get("displayName").isNull()).isTrue();
    assertThat(user.get("profileVisible").asBoolean()).isFalse();
  }

  @ParameterizedTest(name = "{0} × {1}")
  @MethodSource("statusTimesVisibility")
  @DisplayName("profileVisible 与公开主页的 404 / 200 逐格一致：匿名、他人、本人")
  void should_match_public_profile_status_for_every_viewer(String status, String visibility) {
    String username = (status + "-" + visibility).toLowerCase().replace('_', '-');
    long ownerId = userIdOf(register(username + "@example.com", username));
    long otherId = userIdOf(register(username + ".o@example.com", username + "-o"));
    HttpHeaders owner = assertionFor(ownerId, latestSessionIdOf(ownerId));
    HttpHeaders other = assertionFor(otherId, latestSessionIdOf(otherId));
    setPrivacy(owner, visibility);
    if (!status.equals("ACTIVE_UNVERIFIED")) {
      jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", ownerId);
    }
    applyStatus(ownerId, status);

    assertSameVisibility(username, ownerId, noHeaders(), null, "匿名");
    assertSameVisibility(username, ownerId, other, otherId, "他人");
    assertSameVisibility(username, ownerId, owner, ownerId, "本人");
  }

  /// 五种账号状态（`ACTIVE` 再分邮箱已验证与未验证）× 三档主页可见性。
  ///
  /// @return 参数
  static Stream<Arguments> statusTimesVisibility() {
    return Stream.of("ACTIVE", "ACTIVE_UNVERIFIED", "PENDING_DELETION", "BANNED", "DELETED")
        .flatMap(
            status ->
                Stream.of("PUBLIC", "SIGNED_IN", "SELF")
                    .map(visibility -> Arguments.of(status, visibility)));
  }

  /// 同一位访客：摘要的 `profileVisible` 等于公开主页是否 200。
  ///
  /// @param username 主人的用户名
  /// @param ownerId 主人的用户 ID
  /// @param requester 访客请求主页时带的头
  /// @param viewerId 访客的用户 ID，匿名为 `null`
  /// @param label 断言描述
  private void assertSameVisibility(
      String username, long ownerId, HttpHeaders requester, Long viewerId, String label) {
    boolean pageVisible = get("/users/" + username, requester).status() == 200;
    boolean summaryVisible =
        summaries(List.of(ownerId), viewerId).get(0).get("profileVisible").asBoolean();
    assertThat(summaryVisible).as(label).isEqualTo(pageVisible);
  }

  /// 调摘要接口，断言 200，返回 `users`。
  ///
  /// @param userIds 用户 ID
  /// @param viewerId 访客的用户 ID，匿名为 `null`
  /// @return `users`
  private JsonNode summaries(List<Long> userIds, Long viewerId) {
    Map<String, Object> body = new HashMap<>();
    body.put("userIds", userIds);
    body.put("viewerUserId", viewerId);
    RawResponse response = post(SUMMARIES, body, noHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return json(response).get("users");
  }

  /// 主人改主页可见性。
  ///
  /// @param owner 主人的断言头
  /// @param visibility 主页可见性
  private void setPrivacy(HttpHeaders owner, String visibility) {
    RawResponse saved =
        exchange(
            HttpMethod.PUT,
            "/me/privacy",
            Map.of(
                "profileVisibility",
                visibility,
                "professionalVisibility",
                "PUBLIC",
                "searchIndexing",
                false),
            owner);
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  /// 用 SQL 把账号改到给定状态（`ACTIVE`、`ACTIVE_UNVERIFIED` 不动）。
  ///
  /// @param userId 用户 ID
  /// @param status 状态
  private void applyStatus(long userId, String status) {
    switch (status) {
      case "PENDING_DELETION" ->
          jdbcTemplate.update(
              "UPDATE idn_user SET status = 'PENDING_DELETION',"
                  + " deletion_due_at = now() + interval '15 days' WHERE id = ?",
              userId);
      case "BANNED" ->
          jdbcTemplate.update(
              "UPDATE idn_user SET status = 'BANNED', banned_at = now() WHERE id = ?", userId);
      case "DELETED" -> markDeleted(userId);
      default -> {}
    }
  }

  /// 用 SQL 标成已注销：只改用户行，不动资料（隐私设置保留，矩阵要三档都测）。
  ///
  /// @param userId 用户 ID
  private void markDeleted(long userId) {
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'DELETED', deleted_at = now(), email = NULL,"
            + " email_verified_at = NULL WHERE id = ?",
        userId);
  }
}
