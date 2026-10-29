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

/// `idn_user_former_username` 表的 JPA 实体。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_former_username")
public class FormerUsernameEntity extends BaseJpaEntity {

  /// 原主的用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 旧名，规范化之后的小写
  @Column(name = "username", nullable = false, length = 30)
  private String username;

  /// 改名时间
  @Column(name = "replaced_at", nullable = false)
  private Instant replacedAt;

  /// 保留到期时间（不含）
  @Column(name = "reserved_until", nullable = false)
  private Instant reservedUntil;
}
