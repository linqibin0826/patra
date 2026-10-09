package dev.linqibin.patra.identity.domain.model.read;

import dev.linqibin.patra.identity.domain.model.enums.UserStatus;
import java.util.Objects;

/// 「当前用户」接口要的账号信息。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
/// @param status 状态
public record UserAccountReadModel(long userId, String email, UserStatus status) {

  /// 校验非空。
  public UserAccountReadModel {
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(status, "status 不能为 null");
  }

  /// 创建读模型。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @param status 状态
  /// @return 读模型
  public static UserAccountReadModel of(long userId, String email, UserStatus status) {
    return new UserAccountReadModel(userId, email, status);
  }
}
