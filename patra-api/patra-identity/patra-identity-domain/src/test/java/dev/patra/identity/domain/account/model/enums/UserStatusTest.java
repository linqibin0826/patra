package dev.patra.identity.domain.account.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/// UserStatus 单元测试：哪些状态下发头像。
class UserStatusTest {

  @Test
  void should_serve_avatar_only_when_active_or_pending_deletion() {
    assertThat(UserStatus.ACTIVE.servesAvatar()).isTrue();
    assertThat(UserStatus.PENDING_DELETION.servesAvatar()).isTrue();
    assertThat(UserStatus.BANNED.servesAvatar()).isFalse();
    assertThat(UserStatus.DELETED.servesAvatar()).isFalse();
  }
}
