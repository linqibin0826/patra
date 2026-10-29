package dev.patra.identity.infra.account.persistence.entity;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// `idn_user_profile_link` 表的 JPA 实体：资料里的一条链接。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_profile_link")
public class UserProfileLinkEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 用户排好的顺序，从 0 开始
  @Column(name = "position", nullable = false)
  private short position;

  /// 标题，为 null 时门户显示域名
  @Column(name = "title", length = 120)
  private String title;

  /// URL
  @Column(name = "url", nullable = false, length = 300)
  private String url;
}
