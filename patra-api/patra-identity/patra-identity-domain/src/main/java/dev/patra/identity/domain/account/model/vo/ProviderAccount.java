package dev.patra.identity.domain.account.model.vo;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import java.util.Objects;
import lombok.Builder;

/// 第三方平台确认过的账号：domain 和 app 只看到它，不沾框架的类型（v0.19 决策 F）。
///
/// @param provider 平台
/// @param subject 规范化之后的主体
/// @param accountLabel 设置页显示的账号标识
/// @param suggestedUsername 平台给的用户名建议，可以为 `null`，还没按用户名规则校验
/// @param suggestedEmail 平台给的邮箱，可以为 `null`，只作预填，永不用于合并账号
@Builder
public record ProviderAccount(
    IdentityProvider provider,
    String subject,
    String accountLabel,
    String suggestedUsername,
    String suggestedEmail) {

  /// 前三个字段不能为 `null`。
  public ProviderAccount {
    Objects.requireNonNull(provider, "provider 不能为 null");
    Objects.requireNonNull(subject, "subject 不能为 null");
    Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
  }
}
