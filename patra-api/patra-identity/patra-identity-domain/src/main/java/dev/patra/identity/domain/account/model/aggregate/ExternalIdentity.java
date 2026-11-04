package dev.patra.identity.domain.account.model.aggregate;

import dev.patra.identity.domain.account.model.enums.IdentityProvider;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;

/// 外部身份聚合根：一个用户绑定的一个第三方账号。第三方登录按 `(provider, subject)` 找到账号。
///
/// 只存确认身份要用的东西，不存令牌和资料快照（v0.19 决策 C）。
@Getter
public final class ExternalIdentity {

  private Long id;
  private Long userId;
  private IdentityProvider provider;
  private String subject;
  private String accountLabel;
  private Instant linkedAt;
  private Instant lastLoginAt;
  private Long version;

  /// 只能经工厂方法创建。
  private ExternalIdentity() {}

  /// 绑定一个第三方账号。ID 由仓储分配。
  ///
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @param subject 规范化之后的主体
  /// @param accountLabel 设置页显示的账号标识
  /// @param now 绑定时间
  /// @return 外部身份
  public static ExternalIdentity link(
      long userId, IdentityProvider provider, String subject, String accountLabel, Instant now) {
    ExternalIdentity identity = new ExternalIdentity();
    identity.userId = userId;
    identity.provider = Objects.requireNonNull(provider, "provider 不能为 null");
    identity.subject = Objects.requireNonNull(subject, "subject 不能为 null");
    identity.accountLabel = Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
    identity.linkedAt = Objects.requireNonNull(now, "now 不能为 null");
    return identity;
  }

  /// 从库里恢复。
  ///
  /// @param id ID
  /// @param userId 用户 ID
  /// @param provider 平台
  /// @param subject 主体
  /// @param accountLabel 账号标识
  /// @param linkedAt 绑定时间
  /// @param lastLoginAt 最近一次用它登录的时间，可以为 `null`
  /// @param version 乐观锁版本
  /// @return 外部身份
  @Builder(builderMethodName = "restore")
  private static ExternalIdentity fromStorage(
      Long id,
      Long userId,
      IdentityProvider provider,
      String subject,
      String accountLabel,
      Instant linkedAt,
      Instant lastLoginAt,
      Long version) {
    ExternalIdentity identity = new ExternalIdentity();
    identity.id = Objects.requireNonNull(id, "id 不能为 null");
    identity.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    identity.provider = Objects.requireNonNull(provider, "provider 不能为 null");
    identity.subject = Objects.requireNonNull(subject, "subject 不能为 null");
    identity.accountLabel = Objects.requireNonNull(accountLabel, "accountLabel 不能为 null");
    identity.linkedAt = Objects.requireNonNull(linkedAt, "linkedAt 不能为 null");
    identity.lastLoginAt = lastLoginAt;
    identity.version = version;
    return identity;
  }

  /// 用它登录了一次：账号标识可能变了（GitHub 改名、Google 换邮箱），跟着更新。
  ///
  /// @param latestAccountLabel 平台这次给的账号标识
  /// @param now 登录时间
  public void recordLogin(String latestAccountLabel, Instant now) {
    accountLabel = Objects.requireNonNull(latestAccountLabel, "latestAccountLabel 不能为 null");
    lastLoginAt = Objects.requireNonNull(now, "now 不能为 null");
  }
}
