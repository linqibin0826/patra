package dev.linqibin.patra.identity.domain.port.throttle;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.util.Objects;

/// 一次被放行的登录尝试。结算时凭它找到自己的在途登记。
///
/// @param accountType 账号类型
/// @param email 邮箱
/// @param ticketId 在途登记的随机 ID
public record LoginAttempt(AccountType accountType, EmailAddress email, String ticketId) {

  /// 校验非空。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @param ticketId 在途登记的随机 ID
  public LoginAttempt {
    Objects.requireNonNull(accountType, "accountType 不能为 null");
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(ticketId, "ticketId 不能为 null");
  }

  /// 创建登录尝试。
  ///
  /// @param accountType 账号类型
  /// @param email 邮箱
  /// @param ticketId 在途登记的随机 ID
  /// @return 登录尝试
  public static LoginAttempt of(AccountType accountType, EmailAddress email, String ticketId) {
    return new LoginAttempt(accountType, email, ticketId);
  }
}
