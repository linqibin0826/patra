package com.patra.catalog.infra.persistence.entity;

import com.patra.catalog.domain.model.vo.organization.AdminInfo;
import com.patra.catalog.domain.model.vo.organization.OrganizationLink;
import com.patra.starter.jpa.entity.SoftDeletableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 机构 JPA 实体，映射到表 `cat_organization`。
///
/// **基于 ROR (Research Organization Registry) Schema v2.0 设计**。
///
/// **嵌入式值对象（JSON 字段）**：
///
/// - `types` - 机构类型集合（EDUCATION, HEALTHCARE 等）
/// - `domains` - 域名列表
/// - `links` - 网站链接（website, wikipedia）
/// - `admin_info` - ROR 管理元数据（创建/修改日期和版本）
///
/// **子表（逻辑外键关联）**：
///
/// | 子表 | 说明 |
/// |------|------|
/// | cat_organization_name | 多语言名称 |
/// | cat_organization_external_id | 外部标识符（GRID, ISNI 等） |
/// | cat_organization_relation | 机构关系（父子、继任等） |
/// | cat_organization_location | 地理位置 |
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "cat_organization",
    uniqueConstraints = {@UniqueConstraint(name = "uk_ror_id", columnNames = "ror_id")},
    indexes = {
      @Index(name = "idx_org_display_name", columnList = "display_name"),
      @Index(name = "idx_org_status", columnList = "status"),
      @Index(name = "idx_org_dedup_key", columnList = "dedup_key")
    })
public class OrganizationEntity extends SoftDeletableJpaEntity {

  // ========================================
  // 核心标识
  // ========================================

  /// ROR ID（唯一标识符，如 "03vek6s52"）。
  ///
  /// 不含 URL 前缀，仅存储 ID 部分。
  @Column(name = "ror_id", nullable = false, length = 50)
  private String rorId;

  // ========================================
  // 基本信息
  // ========================================

  /// 显示名称（主名称）。
  @Column(name = "display_name", nullable = false, length = 500)
  private String displayName;

  /// 机构状态：ACTIVE/INACTIVE/WITHDRAWN。
  @Column(name = "status", nullable = false, length = 20)
  private String status;

  /// 成立年份。
  @Column(name = "established")
  private Integer established;

  // ========================================
  // JSON 存储字段
  // ========================================

  /// 机构类型集合（JSON 数组）。
  ///
  /// 存储枚举代码：["EDUCATION", "HEALTHCARE"]
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "types", columnDefinition = "JSON")
  private Set<String> types;

  /// 域名列表（JSON 数组）。
  ///
  /// 示例：["harvard.edu", "hms.harvard.edu"]
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "domains", columnDefinition = "JSON")
  private List<String> domains;

  /// 网站链接（JSON 数组）。
  ///
  /// 包含 website 和 wikipedia 链接。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "links", columnDefinition = "JSON")
  private List<OrganizationLink> links;

  /// ROR 管理元数据（JSON 对象）。
  ///
  /// 包含创建日期、修改日期及对应的 Schema 版本。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "admin_info", columnDefinition = "JSON")
  private AdminInfo adminInfo;

  // ========================================
  // 扩展字段
  // ========================================

  /// 去重键（MD5 哈希）。
  ///
  /// 用于跨数据源去重匹配。
  @Column(name = "dedup_key", length = 32)
  private String dedupKey;

  /// 扩展元数据（JSON）。
  ///
  /// 存储来源特定的额外信息。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSON")
  private String metadata;
}
