package com.patra.catalog.infra.persistence.jpa.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.catalog.domain.model.enums.AffiliationType;
import com.patra.starter.jpa.entity.BaseJpaEntity;
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

/// 机构 JPA 实体，映射到表 `cat_affiliation`。
///
/// **设计说明**：
///
/// - 继承 `BaseJpaEntity` 获得审计、乐观锁、软删除功能
/// - 使用 Hibernate 6.6 的 `@JdbcTypeCode(SqlTypes.JSON)` 处理 JSON 字段
/// - `AffiliationType` 枚举通过 `AffiliationTypeConverter` 自动转换
///
/// **索引设计**：
///
/// - `uk_ror`：ROR ID 唯一索引（部分唯一，仅对非 NULL）
/// - `uk_grid`：GRID ID 唯一索引（部分唯一，仅对非 NULL）
/// - `idx_dedup_key`：去重键索引
/// - `idx_country`：国家索引
/// - `idx_name`：机构名称索引
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
    name = "cat_affiliation",
    indexes = {
      @Index(name = "uk_ror", columnList = "ror_id", unique = true),
      @Index(name = "uk_grid", columnList = "grid_id", unique = true),
      @Index(name = "idx_dedup_key", columnList = "dedup_key"),
      @Index(name = "idx_country", columnList = "country"),
      @Index(name = "idx_name", columnList = "name")
    })
public class AffiliationEntity extends BaseJpaEntity {

  // ========== 名称信息 ==========

  /// 机构名称（标准化后）
  @Column(name = "name", nullable = false, length = 500)
  private String name;

  /// 原始名称（外部采集，未标准化）
  @Column(name = "original_name", length = 500)
  private String originalName;

  // ========== 层次结构 ==========

  /// 部门/科室（如 "Department of Medicine"）
  @Column(name = "department", length = 200)
  private String department;

  /// 分部/分院（如 "School of Medicine"）
  @Column(name = "division", length = 200)
  private String division;

  /// 科/组（如 "Cardiology Section"）
  @Column(name = "section", length = 200)
  private String section;

  // ========== 地理位置 ==========

  /// 城市（如 "Boston"）
  @Column(name = "city", length = 100)
  private String city;

  /// 州/省（如 "Massachusetts"，"广东"）
  @Column(name = "state_province", length = 100)
  private String stateProvince;

  /// 国家（ISO 3166-1 alpha-3，如 "USA"，"CHN"）
  @Column(name = "country", length = 100)
  private String country;

  /// 邮政编码（如 "02115"）
  @Column(name = "postal_code", length = 20)
  private String postalCode;

  // ========== 标识符 ==========

  /// ROR 标识符（如 "https://ror.org/03vek6s52"）
  @Column(name = "ror_id", length = 50)
  private String rorId;

  /// GRID 标识符（如 "grid.38142.3c"）
  @Column(name = "grid_id", length = 50)
  private String gridId;

  /// ISNI 标识符（如 "0000 0004 1936 8948"）
  @Column(name = "isni", length = 50)
  private String isni;

  /// Ringgold ID（如 "1812"）
  @Column(name = "ringgold_id", length = 50)
  private String ringgoldId;

  // ========== 关系和分类 ==========

  /// 上级机构（如 "Harvard University"）
  @Column(name = "parent_affiliation", length = 200)
  private String parentAffiliation;

  /// 机构类型（通过 AffiliationTypeConverter 自动转换）
  @Column(name = "affiliation_type", length = 50)
  private AffiliationType affiliationType;

  // ========== 去重和元数据 ==========

  /// 复合去重键（MD5 哈希，应用层计算）
  @Column(name = "dedup_key", length = 255)
  private String dedupKey;

  /// 机构元数据（JSON 格式，灵活扩展）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "JSON")
  private JsonNode metadata;
}
