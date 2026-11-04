package dev.patra.identity.domain.account.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// RegistrationCode 单元测试。
@DisplayName("RegistrationCode 单元测试")
class RegistrationCodeTest {

  @Test
  @DisplayName("生成的都是 6 位数字，前导零不丢")
  void should_generate_six_digits() {
    SecureRandom random = new SecureRandom();
    for (int i = 0; i < 1000; i++) {
      assertThat(RegistrationCode.generate(random).value()).matches("^[0-9]{6}$");
    }
  }

  @Test
  @DisplayName("格式校验：只认 6 位数字，去首尾空白")
  void should_parse_only_six_digits() {
    assertThat(RegistrationCode.parse(" 483920 ")).contains(new RegistrationCode("483920"));
    assertThat(RegistrationCode.parse("48392")).isEmpty();
    assertThat(RegistrationCode.parse("4839200")).isEmpty();
    assertThat(RegistrationCode.parse("48392a")).isEmpty();
    assertThat(RegistrationCode.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("哈希和凭证绑定：同一个码换一个凭证哈希就不同，原文不在哈希里")
  void should_hash_with_ticket() {
    RegistrationCode code = new RegistrationCode("483920");

    assertThat(code.hash("ticket-a")).hasSize(64).doesNotContain("483920");
    assertThat(code.hash("ticket-a")).isEqualTo(code.hash("ticket-a"));
    assertThat(code.hash("ticket-a")).isNotEqualTo(code.hash("ticket-b"));
  }

  @Test
  @DisplayName("toString 不输出原文")
  void should_hide_value_in_to_string() {
    assertThat(new RegistrationCode("483920").toString()).doesNotContain("483920");
  }
}
