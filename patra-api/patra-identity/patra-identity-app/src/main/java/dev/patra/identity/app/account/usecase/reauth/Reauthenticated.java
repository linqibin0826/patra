package dev.patra.identity.app.account.usecase.reauth;

import dev.patra.identity.domain.account.model.read.UserAccountReadModel;
import java.util.Objects;

/// 再认证成功的结果：账号信息，加上新令牌——宽限期内没换令牌时为 `null`，门户保持现有 Cookie。
///
/// @param sessionToken 新令牌原文；没换时为 `null`
/// @param account 账号信息，和「当前用户」接口同一个结构
public record Reauthenticated(String sessionToken, UserAccountReadModel account) {
  /// 账号不能为 `null`；令牌给了就不能是空白。
  public Reauthenticated {
    Objects.requireNonNull(account, "account 不能为 null");
    if (sessionToken != null && sessionToken.isBlank()) {
      throw new IllegalArgumentException("sessionToken 给了就不能是空白");
    }
  }

  /// 创建结果。
  ///
  /// @param sessionToken 新令牌原文，没换时为 `null`
  /// @param account 账号信息
  /// @return 结果
  public static Reauthenticated of(String sessionToken, UserAccountReadModel account) {
    return new Reauthenticated(sessionToken, account);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "Reauthenticated[sessionToken="
        + (sessionToken == null ? "null" : "***")
        + ", account="
        + account
        + "]";
  }
}
