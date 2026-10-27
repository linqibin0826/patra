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

/// @ 联想的完整流程（PAP-118 AC 1）：真实过滤器链、PostgreSQL、Redis。主人出不出现在联想里，与公开主页
/// `GET /users/{username}` 的 404 / 200 逐格对照：只有 `ACTIVE`、访客不是主人、且主页对访客可见时才出现。
/// 昵称一路经 `PUT /me/profile` 设昵称后验证：不分大小写、排在用户名命中之后、同样受可见性与状态约束。
@DisplayName("@ 联想的完整流程")
class UserSuggestionsFlowIT extends IdentityFlowITSupport {

  private static final String SUGGEST = "/_internal/users/suggest";

  /// 拼用户名用的短代号：用户名最长 30 个字符，还要和其他流程测试注册的用户名错开。
  private static final Map<String, String> CODES =
      Map.of(
          "ACTIVE", "act",
          "ACTIVE_UNVERIFIED", "unv",
          "PENDING_DELETION", "pnd",
          "BANNED", "ban",
          "DELETED", "del",
          "PUBLIC", "pub",
          "SIGNED_IN", "sin",
          "SELF", "slf");

  @ParameterizedTest(name = "{0} × {1}")
  @MethodSource("statusTimesVisibility")
  @DisplayName("出现在联想里 ⇔ ACTIVE、不是本人、且公开主页对该访客 200：匿名、他人、本人")
  void should_suggest_only_active_accounts_whose_profile_the_viewer_can_see(
      String status, String visibility) {
    String username = "sg-" + CODES.get(status) + "-" + CODES.get(visibility);
    long ownerId = userIdOf(register(username + "@example.com", username));
    long otherId = userIdOf(register(username + ".o@example.com", username + "-o"));
    HttpHeaders owner = assertionFor(ownerId, latestSessionIdOf(ownerId));
    HttpHeaders other = assertionFor(otherId, latestSessionIdOf(otherId));
    setPrivacy(owner, visibility);
    if (!status.equals("ACTIVE_UNVERIFIED")) {
      markEmailVerified(ownerId);
    }
    applyStatus(ownerId, status);

    assertSuggestedIffVisible(username, ownerId, status, noHeaders(), null, "匿名");
    assertSuggestedIffVisible(username, ownerId, status, other, otherId, "他人");
    assertSuggestedIffVisible(username, ownerId, status, owner, ownerId, "本人");
  }

  /// 五种账号情形（`ACTIVE` 再分邮箱已验证与未验证）× 三档主页可见性。
  ///
  /// @return 参数
  static Stream<Arguments> statusTimesVisibility() {
    return Stream.of("ACTIVE", "ACTIVE_UNVERIFIED", "PENDING_DELETION", "BANNED", "DELETED")
        .flatMap(
            status ->
                Stream.of("PUBLIC", "SIGNED_IN", "SELF")
                    .map(visibility -> Arguments.of(status, visibility)));
  }

  @Test
  @DisplayName("响应项是摘要卡片；按用户名字节序；截到 limit；前缀的大小写与空白不影响")
  void should_return_cards_in_byte_order_and_cut_to_limit() {
    Map<String, Long> ids = new HashMap<>();
    for (String name : List.of("sgo", "sgo-b", "sgo9", "sgo_c", "sgoa")) {
      long id = userIdOf(register(name + "@example.com", name));
      markEmailVerified(id);
      ids.put(name, id);
    }

    JsonNode all = suggest(" SGO ", null, 20);

    assertThat(values(all, "username")).containsExactly("sgo", "sgo-b", "sgo9", "sgo_c", "sgoa");
    assertThat(values(suggest("sgo", null, 2), "username")).containsExactly("sgo", "sgo-b");
    JsonNode first = all.get(0);
    assertThat(first.get("userId").asString()).isEqualTo(String.valueOf(ids.get("sgo")));
    assertThat(first.get("displayName").asString()).isEqualTo("sgo");
    assertThat(first.get("avatarUrl").isNull()).isTrue();
    assertThat(first.get("accountStatus").asString()).isEqualTo("ACTIVE");
    assertThat(first.get("emailVerified").asBoolean()).isTrue();
    assertThat(first.get("profileVisible").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("不可能是用户名开头的前缀：200 空结果；条数越界、缺前缀：422")
  void should_answer_empty_for_impossible_prefix_and_422_for_bad_request() {
    assertThat(values(suggest("-sgo", null, 10), "userId")).isEmpty();
    assertThat(values(suggest("陈", null, 10), "userId")).isEmpty();

    RawResponse tooMany = post(SUGGEST, Map.of("prefix", "sgo", "limit", 21), noHeaders());
    assertThat(tooMany.status()).as(tooMany.body()).isEqualTo(422);
    assertThat(json(tooMany).get("errors").get(0).get("field").asString()).isEqualTo("limit");
    RawResponse noPrefix = post(SUGGEST, Map.of("limit", 10), noHeaders());
    assertThat(noPrefix.status()).as(noPrefix.body()).isEqualTo(422);
    assertThat(json(noPrefix).get("errors").get(0).get("field").asString()).isEqualTo("prefix");
  }

  @Test
  @DisplayName("昵称前缀不分大小写；用户名命中在前、昵称命中在后，两路都命中的只出一次")
  void should_suggest_by_display_name_after_username_hits_without_duplicates() {
    long dup = registerVerified("kunpeng-dup");
    setDisplayName(dup, "Kunpeng Dup");
    registerVerified("kunpeng-z");
    long nick = registerVerified("nk-kp");
    setDisplayName(nick, "Kunpeng Li");

    assertThat(values(suggest("KUNPENG", null, 20), "username"))
        .containsExactly("kunpeng-dup", "kunpeng-z", "nk-kp");
  }

  @Test
  @DisplayName("昵称命中的人同样受可见性与账号状态约束：封禁的、主页仅自己的不出现")
  void should_apply_visibility_and_status_rules_to_display_name_hits() {
    long shown = registerVerified("nk-kun-a");
    setDisplayName(shown, "鲲鹏");
    long banned = registerVerified("nk-kun-b");
    setDisplayName(banned, "鲲鱼");
    applyStatus(banned, "BANNED");
    long hidden = registerVerified("nk-kun-c");
    setDisplayName(hidden, "鲲影");
    setPrivacy(assertionFor(hidden, latestSessionIdOf(hidden)), "SELF");
    long viewer = registerVerified("nk-kun-v");

    assertThat(values(suggest("鲲", viewer, 20), "username")).containsExactly("nk-kun-a");
  }

  /// 注册并用 SQL 标成邮箱已验证（主页才对别人可见）。
  ///
  /// @param username 用户名，邮箱按它拼
  /// @return 用户 ID
  private long registerVerified(String username) {
    long id = userIdOf(register(username + "@example.com", username));
    markEmailVerified(id);
    return id;
  }

  /// 以本人身份经 `PUT /me/profile` 设昵称，断言 200。
  ///
  /// @param userId 用户 ID
  /// @param displayName 昵称
  private void setDisplayName(long userId, String displayName) {
    RawResponse saved =
        exchange(
            HttpMethod.PUT,
            "/me/profile",
            Map.of("displayName", displayName),
            assertionFor(userId, latestSessionIdOf(userId)));
    assertThat(saved.status()).as(saved.body()).isEqualTo(200);
  }

  /// 同一位访客：主人出现在联想里，当且仅当 `ACTIVE`、访客不是主人、且主页对访客 200。
  ///
  /// @param username 主人的用户名，也当前缀用
  /// @param ownerId 主人的用户 ID
  /// @param status 主人的账号情形
  /// @param requester 访客请求主页时带的头
  /// @param viewerId 访客的用户 ID，匿名为 `null`
  /// @param label 断言描述
  private void assertSuggestedIffVisible(
      String username,
      long ownerId,
      String status,
      HttpHeaders requester,
      Long viewerId,
      String label) {
    boolean pageVisible = get("/users/" + username, requester).status() == 200;
    boolean expected =
        pageVisible && status.startsWith("ACTIVE") && !Long.valueOf(ownerId).equals(viewerId);
    boolean suggested =
        values(suggest(username, viewerId, 20), "userId").contains(String.valueOf(ownerId));
    assertThat(suggested).as(label).isEqualTo(expected);
  }

  /// 调联想，断言 200，返回 `users`。
  ///
  /// @param prefix 前缀
  /// @param viewerId 访客的用户 ID，匿名为 `null`
  /// @param limit 条数上限
  /// @return `users`
  private JsonNode suggest(String prefix, Long viewerId, int limit) {
    Map<String, Object> body = new HashMap<>();
    body.put("prefix", prefix);
    body.put("viewerUserId", viewerId);
    body.put("limit", limit);
    RawResponse response = post(SUGGEST, body, noHeaders());
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return json(response).get("users");
  }

  /// 每一项某个字段的字符串值。
  ///
  /// @param users `users` 数组
  /// @param field 字段名
  /// @return 值，按数组顺序
  private static List<String> values(JsonNode users, String field) {
    List<String> values = new ArrayList<>();
    users.forEach(user -> values.add(user.get(field).asString()));
    return values;
  }
}
