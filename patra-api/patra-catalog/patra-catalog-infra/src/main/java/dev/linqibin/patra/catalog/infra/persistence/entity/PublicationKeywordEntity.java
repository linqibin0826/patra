package dev.linqibin.patra.catalog.infra.persistence.entity;

import dev.linqibin.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/// 文献关键词关联 JPA 实体，映射到表 `cat_publication_keyword`。
///
/// **设计说明**：
///
/// - 继承 `ValueObjectJpaEntity`，采用 DELETE/INSERT 模式管理
/// - 通过 `keywordId` 外键关联到 `cat_keyword` 表（规范化设计）
/// - 支持主/副关键词标记和关键词集分类
///
/// **业务含义**：
///
/// 关键词用于描述文章的核心主题，帮助检索和分类。
/// 规范化设计的优势：
/// - 去重：相同关键词只存一份
/// - 检索：可直接通过 `cat_keyword` 表搜索
/// - 统计：支持热门关键词排行
///
/// **索引设计**：
///
/// - `idx_pub_keyword`：文献+关键词复合索引
/// - `idx_keyword_pub`：关键词+文献复合索引（反向查询）
/// - `idx_major`：关键词+主/副标记复合索引
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
    name = "cat_publication_keyword",
    indexes = {
      @Index(name = "idx_pub_keyword", columnList = "publication_id, keyword_id"),
      @Index(name = "idx_keyword_pub", columnList = "keyword_id, publication_id"),
      @Index(name = "idx_major", columnList = "keyword_id, is_major")
    })
public class PublicationKeywordEntity extends ValueObjectJpaEntity {

  // ========== 关联信息 ==========

  /// 出版物 ID（外键：cat_publication.id）。
  @Column(name = "publication_id", nullable = false)
  private Long publicationId;

  /// 关键词 ID（外键：cat_keyword.id）。
  @Column(name = "keyword_id", nullable = false)
  private Long keywordId;

  // ========== 关联元数据 ==========

  /// 是否为主要关键词。
  @Column(name = "is_major", nullable = false)
  @lombok.Builder.Default
  private Boolean major = false;

  /// 顺序号（在同一文献内的排序）。
  @Column(name = "order_num")
  private Integer orderNum;

  /// 关键词集（如 Author/Editor，区分不同来源）。
  @Column(name = "keyword_set", length = 50)
  private String keywordSet;

  // ========== 便捷方法 ==========

  /// 创建关键词关联记录。
  ///
  /// @param publicationId 出版物 ID
  /// @param keywordId 关键词 ID
  /// @param major 是否主要关键词
  /// @param orderNum 顺序号
  /// @param keywordSet 关键词集
  /// @return 新建的实体
  public static PublicationKeywordEntity of(
      Long publicationId, Long keywordId, boolean major, Integer orderNum, String keywordSet) {
    return PublicationKeywordEntity.builder()
        .publicationId(publicationId)
        .keywordId(keywordId)
        .major(major)
        .orderNum(orderNum)
        .keywordSet(keywordSet)
        .build();
  }
}
