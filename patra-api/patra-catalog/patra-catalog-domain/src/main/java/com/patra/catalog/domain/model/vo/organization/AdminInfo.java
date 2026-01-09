package com.patra.catalog.domain.model.vo.organization;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/// ROR 管理元数据值对象（JSON 嵌入）。
///
/// 字段映射：cat_organization.admin_info（JSON）
///
/// 基于 ROR Schema v2.0 的 admin 字段定义。存储 ROR 记录的
/// 创建和修改元数据，作为 JSON 嵌入主表。
///
/// **ROR Schema 结构**：
///
/// ```json
/// "admin": {
///   "created": {
///     "date": "2019-01-20",
///     "schema_version": "1.0"
///   },
///   "last_modified": {
///     "date": "2024-12-11",
///     "schema_version": "2.1"
///   }
/// }
/// ```
///
/// **字段说明**：
///
/// | 字段 | 说明 |
/// |------|------|
/// | createdDate | ROR 记录创建日期 |
/// | createdSchemaVersion | 创建时的 ROR Schema 版本 |
/// | lastModifiedDate | 最后修改日期 |
/// | lastModifiedSchemaVersion | 最后修改时的 ROR Schema 版本 |
///
/// **Jackson 注解设计决策**：
///
/// - **@JsonIgnoreProperties(ignoreUnknown = true)**：防御性设计，忽略未知字段
/// - **@JsonIgnore**：标记 `isXxx()`/`hasXxx()` 便捷方法，避免被 Jackson 序列化为冗余的布尔属性
///
/// @param createdDate ROR 记录创建日期
/// @param createdSchemaVersion 创建时的 Schema 版本
/// @param lastModifiedDate 最后修改日期
/// @param lastModifiedSchemaVersion 最后修改时的 Schema 版本
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#admin">ROR Admin Field</a>
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminInfo(
    LocalDate createdDate,
    String createdSchemaVersion,
    LocalDate lastModifiedDate,
    String lastModifiedSchemaVersion)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 空管理元数据实例（单例模式）。
  private static final AdminInfo EMPTY = new AdminInfo(null, null, null, null);

  // ========== 工厂方法 ==========

  /// 创建管理元数据。
  ///
  /// @param createdDate 创建日期
  /// @param createdSchemaVersion 创建时的 Schema 版本
  /// @param lastModifiedDate 最后修改日期
  /// @param lastModifiedSchemaVersion 最后修改时的 Schema 版本
  /// @return 管理元数据值对象
  public static AdminInfo of(
      LocalDate createdDate,
      String createdSchemaVersion,
      LocalDate lastModifiedDate,
      String lastModifiedSchemaVersion) {
    return new AdminInfo(
        createdDate, createdSchemaVersion, lastModifiedDate, lastModifiedSchemaVersion);
  }

  /// 获取空管理元数据实例。
  ///
  /// @return 空管理元数据值对象
  public static AdminInfo empty() {
    return EMPTY;
  }

  // ========== 查询方法 ==========

  /// 判断是否为空（无任何信息）。
  ///
  /// @return true 如果所有字段都为空
  @JsonIgnore
  public boolean isEmpty() {
    return createdDate == null
        && createdSchemaVersion == null
        && lastModifiedDate == null
        && lastModifiedSchemaVersion == null;
  }

  /// 判断是否有创建信息。
  ///
  /// @return true 如果有创建日期
  @JsonIgnore
  public boolean hasCreatedInfo() {
    return createdDate != null;
  }

  /// 判断是否有最后修改信息。
  ///
  /// @return true 如果有最后修改日期
  @JsonIgnore
  public boolean hasLastModifiedInfo() {
    return lastModifiedDate != null;
  }

  /// 判断最后修改的 Schema 版本是否为 2.x。
  ///
  /// @return true 如果是 2.x 版本
  @JsonIgnore
  public boolean isSchemaV2() {
    return lastModifiedSchemaVersion != null && lastModifiedSchemaVersion.startsWith("2.");
  }

  @Override
  public String toString() {
    if (isEmpty()) {
      return "AdminInfo[empty]";
    }
    StringBuilder sb = new StringBuilder("AdminInfo[");
    if (lastModifiedDate != null) {
      sb.append("modified=").append(lastModifiedDate);
      if (lastModifiedSchemaVersion != null) {
        sb.append(" (v").append(lastModifiedSchemaVersion).append(")");
      }
    }
    sb.append("]");
    return sb.toString();
  }
}
