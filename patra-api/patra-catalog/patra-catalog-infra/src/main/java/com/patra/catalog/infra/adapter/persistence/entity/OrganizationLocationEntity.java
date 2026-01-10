package com.patra.catalog.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/// 机构地理位置 JPA 实体，映射到表 `cat_organization_location`。
///
/// 存储机构的地理位置信息，基于 GeoNames 数据库。
///
/// **层级结构**：
///
/// - 洲（Continent）：7 个大洲
/// - 国家（Country）：ISO 3166-1 alpha-2
/// - 省/州（Subdivision）：ISO 3166-2（如 US-CA、CN-BJ）
/// - 城市（City）：城市名称
///
/// **坐标精度**：
///
/// 使用 DECIMAL(10,7) 存储经纬度，精度约 1 厘米。
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
    name = "cat_organization_location",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_org_location",
          columnNames = {"org_id", "geonames_id"})
    },
    indexes = {
      @Index(name = "idx_org_loc_org_id", columnList = "org_id"),
      @Index(name = "idx_org_loc_country", columnList = "country_code"),
      @Index(name = "idx_org_loc_geonames", columnList = "geonames_id")
    })
public class OrganizationLocationEntity extends ValueObjectJpaEntity {

  /// 所属机构 ID（逻辑外键）。
  @Column(name = "org_id", nullable = false)
  private Long orgId;

  /// GeoNames 地理位置 ID。
  ///
  /// 用于与 GeoNames 数据库关联，获取详细地理信息。
  @Column(name = "geonames_id")
  private Integer geonamesId;

  // ========================================
  // 洲级别
  // ========================================

  /// 洲代码（如 NA=北美洲、AS=亚洲）。
  @Column(name = "continent_code", length = 2)
  private String continentCode;

  /// 洲名称。
  @Column(name = "continent_name", length = 50)
  private String continentName;

  // ========================================
  // 国家级别
  // ========================================

  /// 国家代码（ISO 3166-1 alpha-2，如 US、CN）。
  @Column(name = "country_code", length = 2)
  private String countryCode;

  /// 国家名称。
  @Column(name = "country_name", length = 100)
  private String countryName;

  // ========================================
  // 省/州级别
  // ========================================

  /// 省/州代码（ISO 3166-2，如 US-CA、CN-BJ）。
  @Column(name = "subdivision_code", length = 10)
  private String subdivisionCode;

  /// 省/州名称。
  @Column(name = "subdivision_name", length = 100)
  private String subdivisionName;

  // ========================================
  // 城市级别
  // ========================================

  /// 城市名称。
  @Column(name = "city_name", length = 200)
  private String cityName;

  // ========================================
  // 坐标
  // ========================================

  /// 纬度（-90 到 +90）。
  @Column(name = "latitude", precision = 10, scale = 7)
  private BigDecimal latitude;

  /// 经度（-180 到 +180）。
  @Column(name = "longitude", precision = 10, scale = 7)
  private BigDecimal longitude;
}
