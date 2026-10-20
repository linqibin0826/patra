package dev.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.model.vo.DisplayName;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/// UserProfile 单元测试。不用 JUnit 的 `@DisplayName`：它和值对象同名。
class UserProfileTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-10-02T00:00:00Z");

  @Test
  void should_create_profile_without_display_name() {
    UserProfile profile = UserProfile.create(7L);

    assertThat(profile.getId()).isNull();
    assertThat(profile.getUserId()).isEqualTo(7L);
    assertThat(profile.getDisplayName()).isEmpty();
  }

  @Test
  void should_rename() {
    UserProfile profile = UserProfile.create(7L);

    profile.rename(new DisplayName("陈宇"));

    assertThat(profile.getDisplayName()).contains(new DisplayName("陈宇"));
  }

  @Test
  void should_restore_all_fields_including_empty_display_name() {
    UserProfile named =
        UserProfile.restore(1L, 7L, new DisplayName("陈宇"), 3L, CREATED_AT, UPDATED_AT);
    UserProfile unnamed = UserProfile.restore(2L, 8L, null, 0L, CREATED_AT, UPDATED_AT);

    assertThat(named.getId()).isEqualTo(1L);
    assertThat(named.getUserId()).isEqualTo(7L);
    assertThat(named.getDisplayName()).contains(new DisplayName("陈宇"));
    assertThat(named.getVersion()).isEqualTo(3L);
    assertThat(named.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(named.getUpdatedAt()).isEqualTo(UPDATED_AT);
    assertThat(unnamed.getDisplayName()).isEmpty();
  }
}
