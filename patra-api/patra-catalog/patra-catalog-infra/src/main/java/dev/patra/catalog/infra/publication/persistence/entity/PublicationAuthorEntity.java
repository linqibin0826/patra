package dev.patra.catalog.infra.publication.persistence.entity;

import dev.patra.tool.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 文献-作者关联 JPA 实体，映射到表 `cat_publication_author`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 管理文献与作者的关联关系
/// - 行内存储作者姓名快照（display_name/last_name/... /orcid），不依赖 `cat_author`
/// - `author_id` 为可空软关联位，仅 ORCID 命中已消歧作者时填充
/// - 记录作者顺序和角色信息
/// - 机构归属信息由 `PublicationAuthorAffiliationEntity` 独立管理（支持多机构）
///
/// **索引设计**：
///
/// - `uk_pub_author`：出版物 ID + 作者 ID 部分唯一索引（仅 `author_id IS NOT NULL` 的行，
///   由 Flyway 独占定义——JPA 无法表达部分索引）
/// - `uk_author_order`：出版物 ID + 作者顺序唯一索引（其首列亦覆盖按出版物的查询，
///   故不再单列出版物索引）
/// - `idx_pub_author_author`：作者软关联索引
/// - `idx_first_author`：第一作者索引
/// - `idx_corresponding`：通讯作者索引
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
          name = "uk_author_order",
          columnNames = {"publication_id", "author_order"})
    },
    indexes = {
      @Index(name = "idx_pub_author_author", columnList = "author_id"),
      @Index(name = "idx_first_author", columnList = "is_first_author"),
      @Index(name = "idx_corresponding", columnList = "is_corresponding_author")
    })
public class PublicationAuthorEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 作者 ID（外键：cat_author.id；软关联位——仅 ORCID 命中已消歧作者时填，其余为 null）。
  @Column(name = "author_id")
  private Long authorId;

  // ========== 行内姓名快照（文献视角的作者展示，不依赖 cat_author） ==========

  /// 展示名（"LastName ForeName" 或集体作者名，恒非空）。
  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  /// 姓氏（LastName）。
  @Column(name = "last_name", length = 200)
  private String lastName;

  /// 名字（ForeName）。
  @Column(name = "fore_name", length = 200)
  private String foreName;

  /// 首字母缩写（Initials）。
  @Column(name = "initials", length = 20)
  private String initials;

  /// 后缀（Suffix，如 Jr、III）。
  @Column(name = "suffix", length = 50)
  private String suffix;

  /// 集体作者名（CollectiveName；个人作者为 null）。
  @Column(name = "collective_name", length = 500)
  private String collectiveName;

  /// 归一化 ORCID（存档位，便于未来重关联/审计；未提供或校验失败为 null）。
  @Column(name = "orcid", length = 19)
  private String orcid;

  // ========== 作者角色信息 ==========

  /// 作者顺序（1=第一作者，2=第二作者...）。
  @Column(name = "author_order", nullable = false)
  private Integer authorOrder;

  /// 是否第一作者。
  @Column(name = "is_first_author", nullable = false)
  @Builder.Default
  private Boolean firstAuthor = false;

  /// 是否通讯作者。
  @Column(name = "is_corresponding_author", nullable = false)
  @Builder.Default
  private Boolean correspondingAuthor = false;

  /// 是否同等贡献作者。
  @Column(name = "is_equal_contribution", nullable = false)
  @Builder.Default
  private Boolean equalContribution = false;

  // ========== 联系方式 ==========

  /// 作者邮箱（通讯作者时常填写）。
  @Column(name = "email", length = 255)
  private String email;

  // ========== 扩展字段 ==========

  /// 作者元数据（JSON 格式，灵活扩展）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "author_metadata", columnDefinition = "JSON")
  private JsonNode authorMetadata;
}
