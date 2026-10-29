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

/// `idn_user_research_interest` 表的 JPA 实体：一个关注领域。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_research_interest")
public class UserResearchInterestEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 用户排好的顺序，从 0 开始
  @Column(name = "position", nullable = false)
  private short position;

  /// MeSH 主题词 UI
  @Column(name = "mesh_ui", nullable = false, length = 10)
  private String meshUi;

  /// 英文名快照
  @Column(name = "mesh_name", nullable = false, length = 255)
  private String meshName;
}
