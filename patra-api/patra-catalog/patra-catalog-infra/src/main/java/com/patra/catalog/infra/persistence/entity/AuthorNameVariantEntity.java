package com.patra.catalog.infra.persistence.entity;

import com.patra.starter.jpa.entity.ChildJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/// 作者名字变体 JPA 实体，映射到表 `cat_author_name_variant`。
///
/// **设计说明**：
///
/// - 存储作者在不同文献中出现的各种名字形式
/// - 解析自 PubMed Computed Authors 的 names 数组
/// - 继承 `ChildJpaEntity` 支持增量同步
///
/// **索引设计**：
///
/// - `uk_author_full`：作者 ID + 原始字符串唯一索引
/// - `idx_author_id`：作者索引
/// - `idx_last_name`：姓氏前缀索引
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
    name = "cat_author_name_variant",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_author_full",
          columnNames = {"author_id", "full_string"})
    },
    indexes = {
      @Index(name = "idx_author_id", columnList = "author_id"),
      @Index(name = "idx_last_name", columnList = "last_name")
    })
public class AuthorNameVariantEntity extends ChildJpaEntity {

  // ========== 关联信息 ==========

  /// 关联的作者实体。
  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "author_id", nullable = false)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private AuthorEntity author;

  // ========== 名字组成部分 ==========

  /// 姓（Last Name/Family Name）。
  @Column(name = "last_name", length = 200)
  private String lastName;

  /// 名（First Name/Given Name，可选）。
  @Column(name = "fore_name", length = 200)
  private String foreName;

  /// 姓名缩写（如 "Z", "JK"）。
  ///
  /// 正常情况下 1-5 字符，最多 10 字符。
  @Column(name = "initials", length = 10)
  private String initials;

  /// 原始字符串（如 "Lu,Zhiyong,Z"）。
  ///
  /// 保存 PubMed 原始数据，便于调试和溯源。
  @Column(name = "full_string", length = 300, nullable = false)
  private String fullString;
}
