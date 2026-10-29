package dev.patra.identity.infra.account.persistence.entity;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/// `idn_email_token` 表的 JPA 实体。`toString()` 不输出哈希、邮箱和 IP。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_email_token")
public class EmailTokenEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 用途：EMAIL_VERIFY / PASSWORD_RESET / EMAIL_CHANGE_CONFIRM / EMAIL_CHANGE_REVERT
  @Column(name = "purpose", nullable = false, length = 32)
  private String purpose;

  /// 令牌发往的邮箱（规范化小写）
  @ToString.Exclude
  @Column(name = "email", nullable = false, length = 254)
  private String email;

  /// 令牌原文的 SHA-256，小写十六进制
  @ToString.Exclude
  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  /// 过期时间
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /// 消费时间，没消费时为 null
  @Column(name = "consumed_at")
  private Instant consumedAt;

  /// 作废时间，没作废时为 null
  @Column(name = "revoked_at")
  private Instant revokedAt;

  /// 签发请求的客户端 IP 文本，未知时为 null
  @ToString.Exclude
  @Column(name = "requested_ip", length = 45)
  private String requestedIp;
}
