package dev.linqibin.patra.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/// ClientType 单元测试。
@DisplayName("ClientType 单元测试")
class ClientTypeTest {

  @Test
  @DisplayName("web 对应网页端")
  void should_return_web_when_code_is_web() {
    assertThat(ClientType.fromCode("web")).contains(ClientType.WEB);
    assertThat(ClientType.WEB.getCode()).isEqualTo("web");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "WEB", "app", " web"})
  @DisplayName("不认识的字符串返回空")
  void should_return_empty_when_code_is_unknown(String code) {
    assertThat(ClientType.fromCode(code)).isEmpty();
  }
}
