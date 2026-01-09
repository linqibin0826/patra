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

/// 作者 ORCID JPA 实体，映射到表 `cat_author_orcid`。
///
/// **设计说明**：
///
/// - 存储作者的 ORCID 标识符
/// - 支持一对多关系（少数作者有多个 ORCID）
/// - 使用精简审计字段（id, version, created_at, updated_at），不继承 BaseJpaEntity
///
/// **索引设计**：
///
/// - `uk_orcid`：ORCID 全局唯一索引
/// - `idx_author_id`：作者索引
///
/// @author linqibin
/// @since 0.1.0
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "cat_author_orcid",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_orcid",
          columnNames = {"orcid"})
    },
    indexes = {@Index(name = "idx_author_id", columnList = "author_id")})
public class AuthorOrcidEntity implements Serializable {

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

  // ========== ORCID 信息 ==========

  /// ORCID 标识符（格式: 0000-0001-2345-6789）。
  ///
  /// 全局唯一，由 ORCID 组织分配。
  @Column(name = "orcid", length = 19, nullable = false, unique = true)
  private String orcid;

  /// 是否为主要 ORCID。
  ///
  /// 如果作者有多个 ORCID，标记首选的一个。
  @Column(name = "is_primary", nullable = false)
  @Builder.Default
  private Boolean primary = true;

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
