package com.patra.catalog.infra.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

/// 作者名字变体 JPA 实体，映射到表 `cat_author_name_variant`。
///
/// **设计说明**：
///
/// - 存储作者在不同文献中出现的各种名字形式
/// - 解析自 PubMed Computed Authors 的 names 数组
/// - 使用精简审计字段（id, version, created_at, updated_at），不继承 BaseJpaEntity
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
public class AuthorNameVariantEntity implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 主键 ID（雪花算法生成）。
  @Id
  @Column(name = "id")
  private Long id;

  // ========== 关联信息 ==========

  /// 关联的作者实体。
  @ManyToOne(fetch = FetchType.LAZY)
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
  @Column(name = "initials", length = 50)
  private String initials;

  /// 原始字符串（如 "Lu,Zhiyong,Z"）。
  ///
  /// 保存 PubMed 原始数据，便于调试和溯源。
  @Column(name = "full_string", length = 300, nullable = false)
  private String fullString;

  // ========== 审计字段（精简版） ==========

  /// 乐观锁版本号。
  @Version
  @Column(name = "version")
  private Long version;

  /// 创建时间。
  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private Instant createdAt;

  /// 更新时间。
  @LastModifiedDate
  @Column(name = "updated_at")
  private Instant updatedAt;
}
