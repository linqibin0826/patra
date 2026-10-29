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
import lombok.experimental.SuperBuilder;

/// `idn_user` 表的 JPA 实体。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user")
public class UserEntity extends BaseJpaEntity {

  /// 规范化之后的邮箱，已注销时为 null
  @Column(name = "email", length = 254)
  private String email;

  /// 邮箱验证时间，未验证时为 null
  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  /// 规范化之后的用户名
  @Column(name = "username", nullable = false, length = 30)
  private String username;

  /// 最近一次改名时间，没改过时为 null
  @Column(name = "username_changed_at")
  private Instant usernameChangedAt;

  /// 状态：ACTIVE / BANNED / PENDING_DELETION / DELETED
  @Column(name = "status", nullable = false, length = 16)
  private String status;

  /// 封禁时间，未封禁时为 null
  @Column(name = "banned_at")
  private Instant bannedAt;

  /// 注销到期时间，不在冷静期时为 null
  @Column(name = "deletion_due_at")
  private Instant deletionDueAt;

  /// 到期前提醒的发送时间，只在冷静期内可能有值
  @Column(name = "deletion_reminded_at")
  private Instant deletionRemindedAt;

  /// 注销完成时间，未注销时为 null
  @Column(name = "deleted_at")
  private Instant deletedAt;
}
