package com.patra.catalog.domain.model.vo.organization;

import cn.hutool.core.lang.Assert;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/// 地理位置值对象。
///
/// 字段映射：cat_organization_location 表
///
/// 基于 ROR Schema v2.0 的 locations 字段定义，结合 GeoNames 数据。
/// 一个机构可以有多个地理位置（如多校区）。
///
/// **字段说明**：
///
/// | 字段 | 说明 | 示例 |
/// |------|------|------|
/// | id | 数据库主键（雪花 ID） | |
/// | geonamesId | GeoNames ID（必填） | 4931972 |
/// | continentCode | 洲代码 | NA |
/// | continentName | 洲名称 | North America |
/// | countryCode | 国家代码（必填） | US |
/// | countryName | 国家名称（必填） | United States |
/// | subdivisionCode | 省/州代码 | MA |
/// | subdivisionName | 省/州名称 | Massachusetts |
/// | cityName | 城市名称 | Cambridge |
/// | latitude | 纬度 | 42.3736 |
/// | longitude | 经度 | -71.1097 |
///
/// **ROR Schema 结构**：
///
/// ```json
/// "locations": [{
///   "geonames_id": 4931972,
///   "geonames_details": {
///     "continent_code": "NA",
///     "continent_name": "North America",
///     "country_code": "US",
///     "country_name": "United States",
///     "country_subdivision_code": "MA",
///     "country_subdivision_name": "Massachusetts",
///     "name": "Cambridge",
///     "lat": 42.3736,
///     "lng": -71.1097
///   }
/// }]
/// ```
///
/// **相等性**：基于 geonamesId
///
/// @param id 数据库主键（持久化后填充）
/// @param geonamesId GeoNames ID
/// @param continentCode 洲代码
/// @param continentName 洲名称
/// @param countryCode 国家代码
/// @param countryName 国家名称
/// @param subdivisionCode 省/州代码
/// @param subdivisionName 省/州名称
/// @param cityName 城市名称
/// @param latitude 纬度
/// @param longitude 经度
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#locations">ROR Locations Field</a>
@SuppressWarnings("java:S6218") // 自定义 equals/hashCode 基于业务语义（geonamesId）
public record GeoLocation(
    Long id,
    Integer geonamesId,
    String continentCode,
    String continentName,
    String countryCode,
    String countryName,
    String subdivisionCode,
    String subdivisionName,
    String cityName,
    BigDecimal latitude,
    BigDecimal longitude)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public GeoLocation {
    Assert.notNull(geonamesId, "GeoNames ID 不能为空");
    Assert.notBlank(countryCode, "国家代码不能为空");
    Assert.notBlank(countryName, "国家名称不能为空");
  }

  // ========== Builder 模式 ==========

  /// 创建 Builder。
  ///
  /// @return Builder 实例
  public static Builder builder() {
    return new Builder();
  }

  /// GeoLocation Builder。
  public static class Builder {
    private Long id;
    private Integer geonamesId;
    private String continentCode;
    private String continentName;
    private String countryCode;
    private String countryName;
    private String subdivisionCode;
    private String subdivisionName;
    private String cityName;
    private BigDecimal latitude;
    private BigDecimal longitude;

    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    public Builder geonamesId(Integer geonamesId) {
      this.geonamesId = geonamesId;
      return this;
    }

    public Builder continentCode(String continentCode) {
      this.continentCode = continentCode;
      return this;
    }

    public Builder continentName(String continentName) {
      this.continentName = continentName;
      return this;
    }

    public Builder countryCode(String countryCode) {
      this.countryCode = countryCode;
      return this;
    }

    public Builder countryName(String countryName) {
      this.countryName = countryName;
      return this;
    }

    public Builder subdivisionCode(String subdivisionCode) {
      this.subdivisionCode = subdivisionCode;
      return this;
    }

    public Builder subdivisionName(String subdivisionName) {
      this.subdivisionName = subdivisionName;
      return this;
    }

    public Builder cityName(String cityName) {
      this.cityName = cityName;
      return this;
    }

    public Builder latitude(BigDecimal latitude) {
      this.latitude = latitude;
      return this;
    }

    public Builder longitude(BigDecimal longitude) {
      this.longitude = longitude;
      return this;
    }

    public GeoLocation build() {
      return new GeoLocation(
          id,
          geonamesId,
          continentCode,
          continentName,
          countryCode,
          countryName,
          subdivisionCode,
          subdivisionName,
          cityName,
          latitude,
          longitude);
    }
  }

  // ========== with-style 方法 ==========

  /// 添加 ID（返回新实例）。
  ///
  /// @param id 数据库主键
  /// @return 带 ID 的新实例
  public GeoLocation withId(Long id) {
    return new GeoLocation(
        id,
        this.geonamesId,
        this.continentCode,
        this.continentName,
        this.countryCode,
        this.countryName,
        this.subdivisionCode,
        this.subdivisionName,
        this.cityName,
        this.latitude,
        this.longitude);
  }

  // ========== 查询方法 ==========

  /// 判断是否已持久化（有 ID）。
  ///
  /// @return true 如果已持久化
  public boolean hasId() {
    return id != null;
  }

  /// 判断是否有坐标信息。
  ///
  /// @return true 如果有经纬度
  public boolean hasCoordinates() {
    return latitude != null && longitude != null;
  }

  /// 判断是否有省/州信息。
  ///
  /// @return true 如果有省/州代码
  public boolean hasSubdivision() {
    return subdivisionCode != null && !subdivisionCode.isBlank();
  }

  /// 判断是否有城市信息。
  ///
  /// @return true 如果有城市名称
  public boolean hasCity() {
    return cityName != null && !cityName.isBlank();
  }

  /// 判断是否有洲信息。
  ///
  /// @return true 如果有洲代码
  public boolean hasContinent() {
    return continentCode != null && !continentCode.isBlank();
  }

  /// 判断是否在指定国家（不区分大小写）。
  ///
  /// @param code 国家代码
  /// @return true 如果在该国家
  public boolean isInCountry(String code) {
    return countryCode != null && countryCode.equalsIgnoreCase(code);
  }

  /// 判断是否在指定洲（不区分大小写）。
  ///
  /// @param code 洲代码
  /// @return true 如果在该洲
  public boolean isInContinent(String code) {
    return continentCode != null && continentCode.equalsIgnoreCase(code);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("GeoLocation[");
    sb.append("geonamesId=").append(geonamesId);
    if (cityName != null) {
      sb.append(", city=").append(cityName);
    }
    if (subdivisionName != null) {
      sb.append(", subdivision=").append(subdivisionName);
    }
    sb.append(", country=").append(countryName);
    sb.append("]");
    return sb.toString();
  }

  /// 业务相等性：geonamesId。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GeoLocation that)) {
      return false;
    }
    return Objects.equals(geonamesId, that.geonamesId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(geonamesId);
  }
}
