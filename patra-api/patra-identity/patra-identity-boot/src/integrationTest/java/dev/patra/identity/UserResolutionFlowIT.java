package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 用户名批量解析的完整流程（PAP-118 AC 2、3）：真实过滤器链、PostgreSQL、Redis。改名走 `PATCH /me/username`，
/// 旧名的保留期按可拨动的测试时钟判。
@DisplayName("用户名批量解析的完整流程")
class UserResolutionFlowIT extends IdentityFlowITSupport {

  private static final String RESOLVE = "/_internal/users/resolve/by-usernames";

  @Test
  @DisplayName("当前名与保留期内的旧名都解析到原用户，到期当刻旧名不再解析；不存在与不合语法的省略")
  void should_resolve_current_and_reserved_former_names_until_expiry() {
    long id = userIdOf(register("rs.alpha@example.com", "rs-alpha"));
    RawResponse renamed =
        exchange(
            HttpMethod.PATCH,
            "/me/username",
            Map.of("username", "rs-beta"),
            assertionFor(id, latestSessionIdOf(id)));
    assertThat(renamed.status()).as(renamed.body()).isEqualTo(200);

    JsonNode users = resolve(List.of("rs-alpha", "RS-Beta", "rs-nobody", "rs alpha"));

    assertThat(values(users, "username")).containsExactly("rs-alpha", "RS-Beta");
    assertThat(values(users, "userId")).containsOnly(String.valueOf(id));
    assertThat(values(users, "accountStatus")).containsOnly("ACTIVE");

    clock.set(T0.plus(Duration.ofDays(90)));
    assertThat(values(resolve(List.of("rs-alpha", "rs-beta")), "username"))
        .containsExactly("rs-beta");
  }

  @ParameterizedTest
  @ValueSource(strings = {"BANNED", "PENDING_DELETION", "DELETED"})
  @DisplayName("非正常账号带状态返回")
  void should_resolve_non_active_accounts_with_their_status(String status) {
    String username = "rs-" + status.toLowerCase(Locale.ROOT).replace('_', '-');
    long id = userIdOf(register(username + "@example.com", username));
    applyStatus(id, status);

    JsonNode user = resolve(List.of(username)).get(0);

    assertThat(user.get("userId").asString()).isEqualTo(String.valueOf(id));
    assertThat(user.get("accountStatus").asString()).isEqualTo(status);
  }

  @Test
  @DisplayName("全部不合语法：200 空结果；101 个：422")
  void should_answer_empty_for_no_usernames_and_422_for_too_many() {
    assertThat(values(resolve(List.of("ab", "-x", "陈")), "userId")).isEmpty();

    RawResponse tooMany =
        post(RESOLVE, Map.of("usernames", Collections.nCopies(101, "rs-alpha")), noHeaders());
    assertThat(tooMany.status()).as(tooMany.body()).isEqualTo(422);
    assertThat(json(tooMany).get("errors").get(0).get("code").asString()).isEqualTo("TOO_MANY");
  }

  /// 调解析，断言 200，返回 `users`。
  ///
  /// @param usernames 用户名原文
  /// @return `users`
  private JsonNode resolve(List<String> usernames) {
    RawResponse response = post(RESOLVE, Map.of("usernames", usernames), noHeaders());
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
