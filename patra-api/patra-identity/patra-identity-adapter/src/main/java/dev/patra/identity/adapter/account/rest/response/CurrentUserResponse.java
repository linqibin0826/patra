package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.adapter.account.rest.converter.AvatarUrls;
import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import dev.patra.tool.core.security.AccountType;
import java.time.Instant;
import lombok.Builder;

/// 「当前用户」接口的响应体，也是登录、注册响应里的 `user`。`userId` 按全局 Jackson 设置输出成字符串。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
/// @param emailVerified 邮箱是否已验证
/// @param username 用户名
/// @param displayName 昵称，没设时等于用户名
/// @param avatarUrl 头像地址（identity 相对路径，带版本），没上传时为 `null`，门户画默认头像
/// @param avatarPalette 默认头像色号（0–11），按用户 ID 算，有没有头像都给
/// @param status 账号状态，只会是 `ACTIVE` 或 `PENDING_DELETION`
/// @param deletionDueAt 注销到期时间，不在冷静期时为 `null`
/// @param nextUsernameChangeAt 下次可以改名的时间，没改过名时为 `null`
/// @param accountType 账号类型的 `code`，本版是 `user`
@Builder
public record CurrentUserResponse(
    Long userId,
    String email,
    boolean emailVerified,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    UserStatus status,
    Instant deletionDueAt,
    Instant nextUsernameChangeAt,
    String accountType) {

  /// 从账号读模型映射。
  ///
  /// @param account 账号读模型
  /// @return 响应
  public static CurrentUserResponse from(UserAccountReadModel account) {
    return CurrentUserResponse.builder()
        .userId(account.userId())
        .email(account.email())
        .emailVerified(account.emailVerified())
        .username(account.username())
        .displayName(account.displayName())
        .avatarUrl(AvatarUrls.of(account.avatar()))
        .avatarPalette(account.avatarPalette())
        .status(account.status())
        .deletionDueAt(account.deletionDueAt())
        .nextUsernameChangeAt(account.nextUsernameChangeAt())
        .accountType(AccountType.USER.getCode())
        .build();
  }
}
