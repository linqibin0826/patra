package dev.patra.identity.domain.account.model.aggregate;

import dev.patra.identity.domain.account.model.enums.EmailTokenPurpose;
import dev.patra.identity.domain.account.model.vo.EmailAddress;
import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/// 一次性邮件令牌聚合根：验证邮箱、重设密码、确认或撤销改邮箱都用它。
///
/// 只存原文的哈希，聚合上拿不到原文。令牌至多被消费或作废一次；过期不落库，按 `expiresAt` 现判。
/// 并发下「只能消费一次」靠仓储的条件更新，`markConsumed`、`markRevoked` 只在内存里记结果。
@Getter
public final class EmailToken {

  private Long id;
  private Long userId;
  private EmailTokenPurpose purpose;
  private EmailAddress email;
  private String tokenHash;
  private Instant expiresAt;
  private Instant consumedAt;
  private Instant revokedAt;

  @Getter(AccessLevel.NONE)
  private String requestedIp;

  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private EmailToken() {}

  /// 从库里恢复。构建器名叫 `restore`：`EmailToken.restore().id(…).purpose(…).build()`。
  ///
  /// @param id 令牌 ID
  /// @param userId 用户 ID
  /// @param purpose 用途
  /// @param email 令牌发往的邮箱
  /// @param tokenHash 令牌原文的哈希
  /// @param expiresAt 过期时间
  /// @param consumedAt 消费时间，没消费时为 `null`
  /// @param revokedAt 作废时间，没作废时为 `null`
  /// @param requestedIp 发起请求的 IP，没有时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间
  /// @param updatedAt 更新时间
  @Builder(builderMethodName = "restore")
  private EmailToken(
      Long id,
      Long userId,
      EmailTokenPurpose purpose,
      EmailAddress email,
      String tokenHash,
      Instant expiresAt,
      Instant consumedAt,
      Instant revokedAt,
      String requestedIp,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id 不能为 null");
    this.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    this.purpose = Objects.requireNonNull(purpose, "purpose 不能为 null");
    this.email = Objects.requireNonNull(email, "email 不能为 null");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash 不能为 null");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    this.consumedAt = consumedAt;
    this.revokedAt = revokedAt;
    this.requestedIp = requestedIp;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /// 签发一枚新令牌：过期时间是现在加用途的有效期，只记原文的哈希。ID 由仓储在保存时分配。
  ///
  /// @param purpose 用途
  /// @param userId 用户 ID
  /// @param email 令牌发往的邮箱
  /// @param secret 令牌原文，只用来算哈希，不留在聚合上
  /// @param now 当前时间
  /// @param requestedIp 发起请求的 IP，可以为 `null`
  /// @return 新令牌，没消费也没作废
  public static EmailToken issue(
      EmailTokenPurpose purpose,
      long userId,
      EmailAddress email,
      EmailTokenSecret secret,
      Instant now,
      String requestedIp) {
    Objects.requireNonNull(purpose, "purpose 不能为 null");
    Objects.requireNonNull(secret, "secret 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    EmailToken token = new EmailToken();
    token.userId = userId;
    token.purpose = purpose;
    token.email = Objects.requireNonNull(email, "email 不能为 null");
    token.tokenHash = secret.hash();
    token.expiresAt = now.plus(purpose.validity());
    token.requestedIp = requestedIp;
    return token;
  }

  /// 发起请求的 IP。
  ///
  /// @return IP；没有时为空
  public Optional<String> getRequestedIp() {
    return Optional.ofNullable(requestedIp);
  }

  /// 是否既没被消费也没被作废。过期不在这里判，见 {@link #isUsableAt(Instant)}。
  ///
  /// @return 没结算时为 `true`
  public boolean isActive() {
    return consumedAt == null && revokedAt == null;
  }

  /// 在某个时刻能不能用：没结算，并且还没到过期时间。到过期那一刻就不能用了。
  ///
  /// @param now 当前时间
  /// @return 能用时为 `true`
  public boolean isUsableAt(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    return isActive() && now.isBefore(expiresAt);
  }

  /// 记下令牌已被消费。
  ///
  /// @param now 当前时间
  /// @throws IllegalStateException 已经消费或作废
  public void markConsumed(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    requireActive("消费");
    consumedAt = now;
  }

  /// 记下令牌已被作废。
  ///
  /// @param now 当前时间
  /// @throws IllegalStateException 已经消费或作废
  public void markRevoked(Instant now) {
    Objects.requireNonNull(now, "now 不能为 null");
    requireActive("作废");
    revokedAt = now;
  }

  /// 结算只能发生一次：已经消费或作废的令牌不能再被结算。
  ///
  /// @param action 要做的动作名，写进异常消息
  /// @throws IllegalStateException 已经消费或作废
  private void requireActive(String action) {
    if (!isActive()) {
      throw new IllegalStateException("令牌已经消费或作废，不能再" + action);
    }
  }

  /// 只输出 ID、用户 ID 和用途，不输出哈希、邮箱和 IP。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "EmailToken[id=" + id + ", userId=" + userId + ", purpose=" + purpose + "]";
  }
}
