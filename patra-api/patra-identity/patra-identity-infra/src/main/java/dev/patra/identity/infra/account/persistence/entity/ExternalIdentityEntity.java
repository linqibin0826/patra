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

/// `idn_user_external_identity` 表的 JPA 实体。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_external_identity")
public class ExternalIdentityEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 平台：`GOOGLE`、`GITHUB`、`ORCID`
  @Column(name = "provider", nullable = false, length = 16)
  private String provider;

  /// 规范化之后的主体
  @Column(name = "subject", nullable = false, length = 255)
  private String subject;

  /// 设置页显示的账号标识
  @Column(name = "account_label", nullable = false, length = 255)
  private String accountLabel;

  /// 绑定时间
  @Column(name = "linked_at", nullable = false)
  private Instant linkedAt;

  /// 最近一次用它登录的时间
  @Column(name = "last_login_at")
  private Instant lastLoginAt;
}
