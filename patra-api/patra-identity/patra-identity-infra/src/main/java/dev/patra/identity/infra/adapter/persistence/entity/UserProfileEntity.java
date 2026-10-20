package dev.patra.identity.infra.adapter.persistence.entity;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// `idn_user_profile` 表的 JPA 实体。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_profile")
public class UserProfileEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 昵称，为 null 表示跟随用户名
  @Column(name = "display_name", length = 120)
  private String displayName;
}
