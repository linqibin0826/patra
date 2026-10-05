package dev.linqibin.patra.starter.security.header;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.common.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;

/// IdentityHeaders 单元测试：身份头的读写约定。
@DisplayName("IdentityHeaders 单元测试")
class IdentityHeadersTest {

  private static final CurrentUser USER =
      CurrentUser.of(1001L, 2001L, AccountType.PORTAL, ClientType.WEB);

  private static HttpHeaders validHeaders() {
    HttpHeaders headers = new HttpHeaders();
    IdentityHeaders.write(headers, USER);
    return headers;
  }

  @Test
  @DisplayName("写入的四个身份头能原样解析回当前用户")
  void should_parse_back_user_written_by_write() {
    HttpHeaders headers = new HttpHeaders();

    IdentityHeaders.write(headers, USER);

    assertThat(headers.getFirst(IdentityHeaders.USER_ID)).isEqualTo("1001");
    assertThat(headers.getFirst(IdentityHeaders.SESSION_ID)).isEqualTo("2001");
    assertThat(headers.getFirst(IdentityHeaders.ACCOUNT_TYPE)).isEqualTo("portal");
    assertThat(headers.getFirst(IdentityHeaders.CLIENT_TYPE)).isEqualTo("web");
    assertThat(IdentityHeaders.parse(headers)).isEqualTo(IdentityParseResult.identified(USER));
  }

  @Test
  @DisplayName("写入会覆盖已有的同名头，不是追加")
  void should_overwrite_existing_headers_when_write() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(IdentityHeaders.USER_ID, "999");
    headers.add(IdentityHeaders.USER_ID, "998");

    IdentityHeaders.write(headers, USER);

    assertThat(headers.getOrEmpty(IdentityHeaders.USER_ID)).containsExactly("1001");
  }

  @Test
  @DisplayName("四个身份头都没有时是「没有身份」")
  void should_be_absent_when_no_identity_headers() {
    assertThat(IdentityHeaders.parse(new HttpHeaders())).isEqualTo(IdentityParseResult.absent());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        IdentityHeaders.USER_ID,
        IdentityHeaders.SESSION_ID,
        IdentityHeaders.ACCOUNT_TYPE,
        IdentityHeaders.CLIENT_TYPE
      })
  @DisplayName("四个身份头缺任何一个都是不合法")
  void should_be_malformed_when_any_identity_header_missing(String missing) {
    HttpHeaders headers = validHeaders();
    headers.remove(missing);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        IdentityHeaders.USER_ID,
        IdentityHeaders.SESSION_ID,
        IdentityHeaders.ACCOUNT_TYPE,
        IdentityHeaders.CLIENT_TYPE
      })
  @DisplayName("同一个身份头出现多次是不合法")
  void should_be_malformed_when_identity_header_repeated(String repeated) {
    HttpHeaders headers = validHeaders();
    headers.add(repeated, headers.getFirst(repeated));

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "0",
        "-1",
        "+5",
        "007",
        "１２３",
        "1.0",
        "abc",
        " 12",
        "12 ",
        "9223372036854775808"
      })
  @DisplayName("用户 ID 不是规范写法的正整数时不合法")
  void should_be_malformed_when_user_id_is_not_canonical_positive_long(String userId) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.USER_ID, userId);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "0", "-1", "007", "abc", "9223372036854775808"})
  @DisplayName("会话 ID 不是规范写法的正整数时不合法")
  void should_be_malformed_when_session_id_is_not_canonical_positive_long(String sessionId) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.SESSION_ID, sessionId);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @Test
  @DisplayName("Long 的最大值是合法的 ID")
  void should_accept_long_max_value_as_id() {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.USER_ID, "9223372036854775807");

    assertThat(IdentityHeaders.parse(headers))
        .isEqualTo(
            IdentityParseResult.identified(
                CurrentUser.of(Long.MAX_VALUE, 2001L, AccountType.PORTAL, ClientType.WEB)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "PORTAL", "admin"})
  @DisplayName("账号类型不认识时不合法")
  void should_be_malformed_when_account_type_unknown(String accountType) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.ACCOUNT_TYPE, accountType);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "WEB", "app"})
  @DisplayName("客户端类型不认识时不合法")
  void should_be_malformed_when_client_type_unknown(String clientType) {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.CLIENT_TYPE, clientType);

    assertThat(IdentityHeaders.parse(headers)).isInstanceOf(IdentityParseResult.Malformed.class);
  }

  @Test
  @DisplayName("不合法的原因里只有头名，没有头的值")
  void should_not_leak_header_values_in_malformed_reason() {
    HttpHeaders headers = validHeaders();
    headers.set(IdentityHeaders.USER_ID, "not-a-number");

    assertThat(IdentityHeaders.parse(headers))
        .isInstanceOfSatisfying(
            IdentityParseResult.Malformed.class,
            malformed ->
                assertThat(malformed.reason())
                    .contains(IdentityHeaders.USER_ID)
                    .doesNotContain("not-a-number"));
  }
}
