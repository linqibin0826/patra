package dev.linqibin.patra.identity.domain.model.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// User 单元测试。
@DisplayName("User 单元测试")
class UserTest {

  private static final EmailAddress EMAIL = EmailAddress.of("chen.yu@example.com");
  private static final Instant FIRST_BAN = Instant.parse("2026-10-06T08:00:00Z");
  private static final Instant SECOND_BAN = Instant.parse("2026-10-06T09:00:00Z");

  @Test
  @DisplayName("注册出来的用户是正常状态，还没有 ID")
  void should_register_active_user_without_id() {
    User user = User.register(EMAIL);

    assertThat(user.getId()).isNull();
    assertThat(user.getEmail()).isEqualTo(EMAIL);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getBannedAt()).isNull();
    assertThat(user.isBanned()).isFalse();
  }

  @Test
  @DisplayName("封禁后状态是封禁，记下封禁时间")
  void should_ban_user() {
    User user = User.register(EMAIL);

    user.ban(FIRST_BAN);

    assertThat(user.isBanned()).isTrue();
    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(user.getBannedAt()).isEqualTo(FIRST_BAN);
  }

  @Test
  @DisplayName("重复封禁不改原来的封禁时间")
  void should_keep_first_ban_time_when_banned_twice() {
    User user = User.register(EMAIL);
    user.ban(FIRST_BAN);

    user.ban(SECOND_BAN);

    assertThat(user.getBannedAt()).isEqualTo(FIRST_BAN);
  }

  @Test
  @DisplayName("解封后回到正常，清掉封禁时间")
  void should_unban_user() {
    User user = User.register(EMAIL);
    user.ban(FIRST_BAN);

    user.unban();

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getBannedAt()).isNull();
  }

  @Test
  @DisplayName("对正常用户解封什么都不变")
  void should_do_nothing_when_unbanning_active_user() {
    User user = User.register(EMAIL);

    user.unban();

    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("从库里恢复时保留全部字段")
  void should_restore_all_fields() {
    Instant createdAt = Instant.parse("2026-10-01T00:00:00Z");
    Instant updatedAt = Instant.parse("2026-10-02T00:00:00Z");

    User user = User.restore(42L, EMAIL, UserStatus.BANNED, FIRST_BAN, 3L, createdAt, updatedAt);

    assertThat(user.getId()).isEqualTo(42L);
    assertThat(user.getStatus()).isEqualTo(UserStatus.BANNED);
    assertThat(user.getBannedAt()).isEqualTo(FIRST_BAN);
    assertThat(user.getVersion()).isEqualTo(3L);
    assertThat(user.getCreatedAt()).isEqualTo(createdAt);
    assertThat(user.getUpdatedAt()).isEqualTo(updatedAt);
  }
}
