package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/// 链接类型枚举。
///
/// 字段映射：cat_organization.links (JSON 数组中的 type 字段)
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 定义的链接类型。
///
/// **链接类型说明**：
///
/// | 类型 | 说明 | 示例 |
/// |------|------|------|
/// | WEBSITE | 机构官方网站 | https://www.harvard.edu |
/// | WIKIPEDIA | Wikipedia 页面 | https://en.wikipedia.org/wiki/Harvard_University |
///
/// **使用示例**：
///
/// ```java
/// LinkType type = LinkType.fromCode("website");
/// if (type.isWebsite()) {
///     // 处理官方网站链接
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#links">ROR Links Field</a>
@Getter
public enum LinkType {

  /// 官方网站
  WEBSITE("website", "官方网站"),

  /// Wikipedia 页面
  WIKIPEDIA("wikipedia", "Wikipedia");

  /// 数据库存储的代码值（与 ROR 一致，小写）。
  private final String code;

  /// 中文描述
  private final String description;

  LinkType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 获取代码值（用于 JSON 序列化）。
  ///
  /// 使用 `@JsonValue` 确保 JSON 序列化时使用 `code`（如 `"website"`）而非枚举名称（`"WEBSITE"`）。
  ///
  /// @return 代码值
  @JsonValue
  public String getCode() {
    return code;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// 使用 `@JsonCreator` 确保 JSON 反序列化时能正确解析 `"website"` 等小写代码值。
  ///
  /// @param value 代码值（如 "website", "WIKIPEDIA"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值为空或无效
  @JsonCreator
  public static LinkType fromCode(String value) {
    Assert.notBlank(value, "链接类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (LinkType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的链接类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static LinkType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    for (LinkType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 判断是否为官方网站链接。
  ///
  /// @return true 如果为 WEBSITE
  public boolean isWebsite() {
    return this == WEBSITE;
  }

  /// 判断是否为 Wikipedia 链接。
  ///
  /// @return true 如果为 WIKIPEDIA
  public boolean isWikipedia() {
    return this == WIKIPEDIA;
  }

  /// 判断是否为外部参考链接（非机构自身的链接）。
  ///
  /// Wikipedia 被视为外部参考链接。
  ///
  /// @return true 如果为外部参考链接
  public boolean isExternal() {
    return this == WIKIPEDIA;
  }
}
