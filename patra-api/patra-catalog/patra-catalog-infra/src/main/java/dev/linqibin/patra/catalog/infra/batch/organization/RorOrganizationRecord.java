package dev.linqibin.patra.catalog.infra.batch.organization;

import java.util.List;

/// ROR v2 JSON 映射 POJO。
///
/// 完整映射 ROR Data Dump 的 JSON 结构，用于反序列化 ROR v2 格式的机构记录。
///
/// **ROR v2 JSON 结构**：
///
/// ```json
/// {
///   "id": "https://ror.org/03yrm5c26",
///   "admin": { "created": {...}, "last_modified": {...} },
///   "domains": ["mit.edu"],
///   "established": 1861,
///   "external_ids": [{ "type": "isni", "preferred": "...", "all": [...] }],
///   "links": [{ "type": "website", "value": "https://..." }],
///   "locations": [{ "geonames_id": 123, "geonames_details": {...} }],
///   "names": [{ "value": "MIT", "types": ["ror_display"], "lang": null }],
///   "relationships": [{ "id": "...", "label": "...", "type": "parent" }],
///   "status": "active",
///   "types": ["education", "funder"]
/// }
/// ```
///
/// **使用方式**：
///
/// ```java
/// ObjectMapper mapper = JsonMapper.builder()
///     .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
///     .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
///     .build();
/// RorOrganizationRecord record = mapper.readValue(json, RorOrganizationRecord.class);
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/v2/docs/fields">ROR Fields Documentation</a>
public record RorOrganizationRecord(
    String id,
    Admin admin,
    List<String> domains,
    Integer established,
    List<ExternalId> externalIds,
    List<Link> links,
    List<Location> locations,
    List<Name> names,
    List<Relationship> relationships,
    String status,
    List<String> types) {

  /// 主构造函数 - 用于 Jackson 反序列化。
  public RorOrganizationRecord {}

  /// 提取 ROR ID 后缀。
  ///
  /// 从完整 URL 中提取 ID，如 `https://ror.org/03yrm5c26` → `03yrm5c26`。
  ///
  /// @return ROR ID 后缀，如果 id 为 null 则返回 null
  public String extractRorId() {
    if (id == null) {
      return null;
    }
    return id.replace("https://ror.org/", "");
  }

  /// 获取 ror_display 类型的名称作为显示名称。
  ///
  /// @return 显示名称，如果不存在则返回 null
  public String getDisplayName() {
    if (names == null || names.isEmpty()) {
      return null;
    }
    return names.stream()
        .filter(n -> n.types() != null && n.types().contains("ror_display"))
        .map(Name::value)
        .findFirst()
        .orElse(null);
  }

  // ========== 嵌套记录类型 ==========

  /// 管理元数据。
  ///
  /// @param created 创建信息
  /// @param lastModified 最后修改信息
  public record Admin(AdminInfo created, AdminInfo lastModified) {}

  /// 管理信息详情。
  ///
  /// @param date 日期（格式：YYYY-MM-DD）
  /// @param schemaVersion Schema 版本
  public record AdminInfo(String date, String schemaVersion) {}

  /// 外部标识符。
  ///
  /// @param type 类型（isni、wikidata、fundref、grid、ringgold）
  /// @param preferred 首选值
  /// @param all 所有值列表
  public record ExternalId(String type, String preferred, List<String> all) {}

  /// 链接。
  ///
  /// @param type 类型（website、wikipedia）
  /// @param value URL 值
  public record Link(String type, String value) {}

  /// 地理位置。
  ///
  /// @param geonamesId GeoNames ID
  /// @param geonamesDetails GeoNames 详情
  public record Location(Integer geonamesId, GeonamesDetails geonamesDetails) {}

  /// GeoNames 详情。
  ///
  /// @param continentCode 洲代码
  /// @param continentName 洲名称
  /// @param countryCode 国家代码（ISO 3166-1 alpha-2）
  /// @param countryName 国家名称
  /// @param countrySubdivisionCode 省/州代码（ISO 3166-2）
  /// @param countrySubdivisionName 省/州名称
  /// @param lat 纬度
  /// @param lng 经度
  /// @param name 城市名称
  public record GeonamesDetails(
      String continentCode,
      String continentName,
      String countryCode,
      String countryName,
      String countrySubdivisionCode,
      String countrySubdivisionName,
      Double lat,
      Double lng,
      String name) {}

  /// 名称。
  ///
  /// @param value 名称值
  /// @param types 名称类型列表（ror_display、label、alias、acronym）
  /// @param lang 语言代码（ISO 639-1，可为 null）
  public record Name(String value, List<String> types, String lang) {}

  /// 机构关系。
  ///
  /// @param id 关联机构的 ROR ID（完整 URL）
  /// @param label 关联机构的名称
  /// @param type 关系类型（parent、child、related、successor、predecessor）
  public record Relationship(String id, String label, String type) {

    /// 提取关联机构的 ROR ID 后缀。
    ///
    /// @return ROR ID 后缀
    public String extractRelatedRorId() {
      if (id == null) {
        return null;
      }
      return id.replace("https://ror.org/", "");
    }
  }
}
