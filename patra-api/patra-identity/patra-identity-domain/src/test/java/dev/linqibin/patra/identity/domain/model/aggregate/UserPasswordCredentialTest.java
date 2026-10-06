package dev.linqibin.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.model.vo.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UserPasswordCredential 单元测试。
@DisplayName("UserPasswordCredential 单元测试")
class UserPasswordCredentialTest {

  private static final PasswordHash HASH =
      PasswordHash.of("$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA");

  @Test
  @DisplayName("新建的凭据还没有 ID，属于指定用户")
  void should_create_credential_for_user() {
    UserPasswordCredential credential = UserPasswordCredential.create(42L, HASH);

    assertThat(credential.getId()).isNull();
    assertThat(credential.getUserId()).isEqualTo(42L);
    assertThat(credential.getPasswordHash()).isEqualTo(HASH);
  }

  @Test
  @DisplayName("toString 不输出哈希")
  void should_hide_hash_in_to_string() {
    UserPasswordCredential credential = UserPasswordCredential.restore(7L, 42L, HASH, 0L);

    assertThat(credential.toString()).doesNotContain("argon2id").contains("42");
  }
}
