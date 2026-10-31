package dev.patra.identity.domain.account.model.read;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import org.junit.jupiter.api.Test;

/// 收件信息的对外形式（PAP-142 spec 第 13 节）：昵称没设时回退成用户名；已注销只留 ID 与状态。
class UserRecipientReadModelTest {

  /// 正常账号原样输出，昵称是原值。
  @Test
  void should_output_active_recipient_as_is() {
    UserRecipientView view = recipient(UserStatus.ACTIVE, "chen@example.com", true, "陈宇").view();

    assertThat(view)
        .isEqualTo(
            UserRecipientView.builder()
                .userId(42L)
                .email("chen@example.com")
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .username("chen-yu")
                .displayName("陈宇")
                .build());
  }

  /// 没设昵称时显示名回退成用户名，同批量用户摘要。
  @Test
  void should_fall_back_to_username_without_display_name() {
    assertThat(recipient(UserStatus.BANNED, "chen@example.com", false, null).view().displayName())
        .isEqualTo("chen-yu");
  }

  /// 已注销：邮箱、用户名、显示名都不给，邮箱算未验证。库里用户名还在，要在这里显式置空。
  @Test
  void should_blank_deleted_recipient() {
    UserRecipientView view = recipient(UserStatus.DELETED, null, false, "陈宇").view();

    assertThat(view.userId()).isEqualTo(42L);
    assertThat(view.status()).isEqualTo(UserStatus.DELETED);
    assertThat(view.email()).isNull();
    assertThat(view.username()).isNull();
    assertThat(view.displayName()).isNull();
    assertThat(view.emailVerified()).isFalse();
  }

  /// 一份收件信息原始数据，用户名固定 `chen-yu`。
  ///
  /// @param status 状态
  /// @param email 邮箱，可空
  /// @param verified 是否已验证
  /// @param displayName 昵称原值，可空
  /// @return 原始数据
  private static UserRecipientReadModel recipient(
      UserStatus status, String email, boolean verified, String displayName) {
    return UserRecipientReadModel.builder()
        .userId(42L)
        .email(email)
        .emailVerified(verified)
        .status(status)
        .username("chen-yu")
        .displayName(displayName)
        .build();
  }
}
