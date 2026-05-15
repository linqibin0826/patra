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

/// 文献-补充 MeSH 概念关联 JPA 实体，映射到表 `cat_publication_suppl_mesh`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 管理文献与 MeSH SCR（Supplementary Concept Records）的关联
/// - 使用 `scr_ui` 作为关联键（与 cat_mesh_scr.ui 关联）
///
/// **业务含义**：
///
/// MeSH SCR（补充概念记录）用于标注 PubMed 文献中的：
/// - 化学物质和药物（Type="Chemical"）
/// - 疾病名称变体（Type="Disease"）
/// - 实验方案（Type="Protocol"）
///
/// 与 MeshHeading 不同，SCR 不在 MeSH 层级树中，而是通过 HeadingMappedTo 关联到正式描述符。
///
/// **索引设计**：
///
/// - `uk_pub_scr`：文献 ID + SCR UI 唯一索引，防止重复标引
/// - `idx_scr_ui`：SCR UI 索引，支持反向查询（查询某个 SCR 的所有文献）
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
    name = "cat_publication_suppl_mesh",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_pub_scr",
          columnNames = {"publication_id", "scr_ui"})
    },
    indexes = {@Index(name = "idx_scr_ui", columnList = "scr_ui")})
public class PublicationSupplMeshEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 文献 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// SCR UI（关联键：cat_mesh_scr.ui）。
  ///
  /// 存储如 "C538003" 格式的唯一标识符。
  @Column(name = "scr_ui", nullable = false, length = 10)
  private String scrUi;

  // ========== 标引元数据 ==========

  /// 补充概念顺序（可选）。
  ///
  /// 保留原始 XML 中的顺序。
  @Column(name = "suppl_order")
  private Integer supplOrder;

  // ========== 便捷方法 ==========

  /// 创建补充 MeSH 概念记录。
  ///
  /// @param publicationId 文献 ID
  /// @param scrUi SCR UI
  /// @param supplOrder 顺序
  /// @return 新建的实体
  public static PublicationSupplMeshEntity of(
      Long publicationId, String scrUi, Integer supplOrder) {
    return PublicationSupplMeshEntity.builder()
        .publicationId(publicationId)
        .scrUi(scrUi)
        .supplOrder(supplOrder)
        .build();
  }
}
