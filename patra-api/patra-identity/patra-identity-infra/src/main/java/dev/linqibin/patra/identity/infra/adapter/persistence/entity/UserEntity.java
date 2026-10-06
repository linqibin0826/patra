package dev.linqibin.patra.identity.infra.adapter.persistence.entity;

import dev.linqibin.starter.jpa.entity.BaseJpaEntity;
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

  /// 规范化之后的邮箱
  @Column(name = "email", nullable = false, length = 254)
  private String email;

  /// 状态：ACTIVE / BANNED
  @Column(name = "status", nullable = false, length = 16)
  private String status;

  /// 封禁时间，未封禁时为 null
  @Column(name = "banned_at")
  private Instant bannedAt;
}
