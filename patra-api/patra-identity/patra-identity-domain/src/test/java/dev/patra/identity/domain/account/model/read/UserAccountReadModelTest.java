package dev.patra.identity.domain.account.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.policy.AvatarPalette;
import org.junit.jupiter.api.Test;

class UserAccountReadModelTest {

  @Test
  void should_derive_avatar_palette_from_user_id() {
    UserAccountReadModel account =
        UserAccountReadModel.builder()
            .userId(352303128713027974L)
            .username("chen-yu")
            .displayName("chen-yu")
            .status(UserStatus.ACTIVE)
            .build();

    assertThat(account.avatarPalette()).isEqualTo(AvatarPalette.of(352303128713027974L));
    assertThat(account.avatar()).isNull();
  }
}
