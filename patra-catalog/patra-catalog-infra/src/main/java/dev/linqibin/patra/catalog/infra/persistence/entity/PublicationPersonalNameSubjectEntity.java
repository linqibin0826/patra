package dev.linqibin.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 人物主题 JPA 实体，映射到表 `cat_publication_personal_name_subject`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 存储传记类/历史类/纪念类文献的主题人物信息
/// - 主题人物是文献内容描述的对象（如历史人物、传记主角）
///
/// **业务场景**：
///
/// - 传记文献：描述某人生平的文章
/// - 历史类文献：涉及历史人物的研究
/// - 纪念类文献：纪念某位学者或研究者的文章
///
/// **索引设计**：
///
/// - `idx_publication`：出版物索引，支持查询某文献的主题人物
/// - `idx_subject_type`：主题类型索引，支持按类型筛选
/// - `idx_last_name`：姓氏索引，支持按姓氏查询历史人物
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
    name = "cat_publication_personal_name_subject",
    indexes = {
      @Index(name = "idx_publication", columnList = "publication_id"),
      @Index(name = "idx_subject_type", columnList = "subject_type"),
      @Index(name = "idx_last_name", columnList = "last_name")
    })
public class PublicationPersonalNameSubjectEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  // ========== 姓名信息 ==========

  /// 姓（Last Name/Family Name）。
  @Column(name = "last_name", length = 200)
  private String lastName;

  /// 名（First Name/Given Name）。
  @Column(name = "fore_name", length = 200)
  private String foreName;

  /// 姓名缩写（如 "J.K."）。
  @Column(name = "initials", length = 50)
  private String initials;

  /// 后缀/头衔（如 "Jr.", "King", "Emperor"）。
  @Column(name = "suffix", length = 100)
  private String suffix;

  // ========== 人物描述 ==========

  /// 生卒年代（如 "1820-1910", "c. 460 BC - c. 370 BC"）。
  @Column(name = "dates", length = 100)
  private String dates;

  /// 人物描述（简短介绍）。
  @Column(name = "description", length = 500)
  private String description;

  /// 主题类型（如 "biography", "history", "memorial"）。
  @Column(name = "subject_type", length = 50)
  private String subjectType;

  /// 人物标识符（如 VIAF ID, Wikidata ID）。
  @Column(name = "identifier", length = 100)
  private String identifier;

  // ========== 排序与扩展 ==========

  /// 顺序号（多个主题人物时排序）。
  @Column(name = "order_num")
  private Integer orderNum;

  /// 人物元数据（JSON 格式，灵活扩展）。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSON")
  private JsonNode metadata;
}
