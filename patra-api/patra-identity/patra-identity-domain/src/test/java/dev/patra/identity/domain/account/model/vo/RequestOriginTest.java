package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// RequestOrigin 单元测试：适配器给的 IP 与 UA 原值的规范化（PAP-99 设计第 8.1 节）。
@DisplayName("RequestOrigin 单元测试")
class RequestOriginTest {

  @Test
  @DisplayName("空白当 null")
  void should_drop_blank_values() {
    RequestOrigin origin = RequestOrigin.of("  ", "\t");

    assertThat(origin.ip()).isNull();
    assertThat(origin.userAgent()).isNull();
    assertThat(RequestOrigin.unknown()).isEqualTo(RequestOrigin.of(null, null));
  }

  @Test
  @DisplayName("UA 按码点截到 512")
  void should_truncate_user_agent_to_512_code_points() {
    String userAgent = RequestOrigin.of(null, "😀".repeat(600)).userAgent();

    assertThat(userAgent.codePointCount(0, userAgent.length()))
        .isEqualTo(RequestOrigin.USER_AGENT_MAX_LENGTH);
  }

  @Test
  @DisplayName("UA 去掉控制字符")
  void should_strip_control_characters_from_user_agent() {
    assertThat(RequestOrigin.of(null, "A\nB\u0000C\u001b").userAgent()).isEqualTo("ABC");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not-an-ip",
        "1.2.3.4.5",
        "localhost",
        "999.1.1.1",
        "1234567890123456789012345678901234567890123456"
      })
  @DisplayName("不是 IP 字面量的当 null：不能当可信输入")
  void should_drop_ip_that_is_not_a_literal(String raw) {
    assertThat(RequestOrigin.of(raw, null).ip()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "203.0.113.9, 203.0.113.9",
    "'[2001:db8::1]', 2001:db8::1",
    "2001:DB8::1, 2001:db8::1",
    "::ffff:1.2.3.4, 1.2.3.4"
  })
  @DisplayName("IP 字面量规范化：去方括号、小写、IPv4 映射地址还原成 IPv4")
  void should_normalize_ip_literal(String raw, String expected) {
    assertThat(RequestOrigin.of(raw, null).ip()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "2408:8207:0:0:0:0:0:1, 2408:8207::1",
    "2001:0DB8:0000:0000:0001:0000:0000:0001, 2001:db8::1:0:0:1",
    "2001:db8:0:0:1:0:0:0, 2001:db8:0:0:1::",
    "2001:db8:0:1:1:1:1:1, 2001:db8:0:1:1:1:1:1",
    "0:0:0:0:0:0:0:1, ::1",
    "0:0:0:0:0:0:0:0, ::",
    "fe80:0:0:0:0:0:0:1%1, fe80::1%1"
  })
  @DisplayName("IPv6 规范成 RFC 5952：去前导零、最长的一段连续零压成 ::（一样长取第一段，单个零不压）")
  void should_normalize_ipv6_to_rfc5952(String raw, String expected) {
    assertThat(RequestOrigin.of(raw, null).ip()).isEqualTo(expected);
  }
}
