package com.patra.catalog.infra.adapter.persistence.entity;

import com.fasterxml.jackson.databind.JsonNode;
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

/// 文献-作者关联 JPA 实体，映射到表 `cat_publication_author`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 管理文献与作者的多对多关系
/// - 记录作者顺序、角色和机构归属（发表时的归属）
///
/// **索引设计**：
///
/// - `uk_pub_author`：出版物 ID + 作者 ID 唯一索引
/// - `uk_author_order`：出版物 ID + 作者顺序唯一索引
/// - `idx_publication`：出版物索引
/// - `idx_author`：作者索引
/// - `idx_first_author`：第一作者索引
/// - `idx_corresponding`：通讯作者索引
/// - `idx_organization`：机构索引
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
    name = "cat_publication_author",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_author",
          columnNames = {"publication_id", "author_id"}),
      @UniqueConstraint(
          name = "uk_author_order",
          columnNames = {"publication_id", "author_order"})
    },
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_author", columnList = "author_id"),
      @Index(name = "idx_first_author", columnList = "is_first_author"),
      @Index(name = "idx_corresponding", columnList = "is_corresponding_author"),
      @Index(name = "idx_organization", columnList = "organization_id")
    })
public class PublicationAuthorEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 作者 ID（外键：cat_author.id）。
  @Column(name = "author_id", nullable = false)
  private Long authorId;

  // ========== 作者角色信息 ==========

  /// 作者顺序（1=第一作者，2=第二作者...）。
  @Column(name = "author_order", nullable = false)
  private Integer authorOrder;

  /// 是否第一作者。
  @Column(name = "is_first_author", nullable = false)
  @lombok.Builder.Default
  private Boolean firstAuthor = false;

  /// 是否通讯作者。
  @Column(name = "is_corresponding_author", nullable = false)
  @lombok.Builder.Default
  private Boolean correspondingAuthor = false;

  /// 是否同等贡献作者。
  @Column(name = "is_equal_contribution", nullable = false)
  @lombok.Builder.Default
  private Boolean equalContribution = false;

  // ========== 机构归属（发表时的归属） ==========

  /// 原始机构字符串（外部采集，未标准化）。
  @Column(name = "affiliation_string", length = 1000)
  private String affiliationString;

  /// 机构 ID（外键：cat_organization.id，延迟填充）。
  @Column(name = "organization_id")
  private Long organizationId;

  // ========== 联系方式 ==========

  /// 作者邮箱（通讯作者时常填写）。
  @Column(name = "email", length = 255)
  private String email;

  // ========== 扩展字段 ==========

  /// 作者元数据（JSON 格式，灵活扩展）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "author_metadata", columnDefinition = "JSON")
  private JsonNode authorMetadata;

  // ========== 便捷方法 ==========

  /// 标记为第一作者。
  public void markAsFirstAuthor() {
    this.authorOrder = 1;
    this.firstAuthor = true;
  }

  /// 标记为通讯作者。
  ///
  /// @param email 通讯邮箱
  public void markAsCorresponding(String email) {
    this.correspondingAuthor = true;
    this.email = email;
  }

  /// 设置机构归属。
  ///
  /// @param affiliationString 原始机构字符串
  /// @param organizationId 标准化后的机构 ID（可选）
  public void setAffiliation(String affiliationString, Long organizationId) {
    this.affiliationString = affiliationString;
    this.organizationId = organizationId;
  }
}
