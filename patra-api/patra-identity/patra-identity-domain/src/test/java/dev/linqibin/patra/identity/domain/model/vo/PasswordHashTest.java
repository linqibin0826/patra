package dev.linqibin.patra.identity.domain.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PasswordHash 单元测试。
@DisplayName("PasswordHash 单元测试")
class PasswordHashTest {

  @Test
  @DisplayName("toString 不输出哈希，只输出 ***")
  void should_hide_hash_in_to_string() {
    PasswordHash hash = PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");

    assertThat(hash.toString()).isEqualTo("***");
  }

  @Test
  @DisplayName("空白的哈希不合法")
  void should_reject_blank_hash() {
    assertThatThrownBy(() -> PasswordHash.of(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PasswordHash.of(null)).isInstanceOf(NullPointerException.class);
  }
}
