package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 文献-研究者关联 JPA 实体，映射到表 `cat_publication_investigator`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 管理文献与研究者的多对多关系
/// - 记录研究者在文献中的角色和顺序信息
///
/// **索引设计**：
///
/// - `uk_pub_investigator`：出版物 ID + 研究者 ID 唯一索引
/// - `idx_publication`：出版物索引，支持查询某文献的所有研究者
/// - `idx_investigator`：研究者索引，支持查询某研究者的所有文献
/// - `idx_role`：角色索引，支持按角色筛选（如查询 PI 的所有文献）
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "cat_publication_investigator",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_investigator",
          columnNames = {"publication_id", "investigator_id"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_investigator", columnList = "investigator_id"),
      @Index(name = "idx_role", columnList = "role")
    })
public class PublicationInvestigatorEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 研究者 ID（外键：cat_investigator.id）。
  @Column(name = "investigator_id", nullable = false)
  private Long investigatorId;

  // ========== 角色信息 ==========

  /// 角色（如 "principal", "co-investigator", "coordinator"）。
  @Column(name = "role", length = 100)
  private String role;

  /// 是否联系人。
  @Column(name = "is_contact", nullable = false)
  @lombok.Builder.Default
  private Boolean contact = false;

  /// 顺序号（多个研究者时排序）。
  @Column(name = "order_num")
  private Integer orderNum;

  /// 职责描述（文本）。
  @Column(name = "responsibility", length = 1000)
  private String responsibility;

  // ========== 扩展字段 ==========

  /// 关联元数据（JSON 格式，灵活扩展）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSON")
  private JsonNode metadata;
}
