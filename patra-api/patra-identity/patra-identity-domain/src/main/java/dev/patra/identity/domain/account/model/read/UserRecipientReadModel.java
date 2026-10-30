package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import java.util.Objects;
import lombok.Builder;

/// 批量收件信息要的原始数据：用户表加资料表里的昵称（`POST /_internal/users/recipients`，PAP-142）。对外输出的是
/// `view()` 按状态处理后的 `UserRecipientView`。
///
/// @param userId 用户 ID
/// @param email 邮箱；已注销时库里已置空，为 `null`
/// @param emailVerified 邮箱是否已验证
/// @param status 状态
/// @param username 用户名（已注销也还在库里）
/// @param displayName 资料里的昵称原值，没设时为 `null`
@Builder
public record UserRecipientReadModel(
    long userId,
    String email,
    boolean emailVerified,
    UserStatus status,
    String username,
    String displayName) {

  /// 校验非空。
  public UserRecipientReadModel {
    Objects.requireNonNull(status, "status 不能为 null");
    Objects.requireNonNull(username, "username 不能为 null");
  }

  /// 按状态出收件信息：已注销只留 ID 与状态；其余昵称没设时回退成用户名。
  ///
  /// @return 收件信息
  public UserRecipientView view() {
    UserRecipientView.UserRecipientViewBuilder view =
        UserRecipientView.builder().userId(userId).status(status);
    if (status == UserStatus.DELETED) {
      return view.emailVerified(false).build();
    }
    return view.email(email)
        .emailVerified(emailVerified)
        .username(username)
        .displayName(displayName != null ? displayName : username)
        .build();
  }
}
