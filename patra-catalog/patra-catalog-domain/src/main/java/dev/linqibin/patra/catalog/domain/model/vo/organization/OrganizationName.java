package dev.linqibin.patra.catalog.domain.model.vo.organization;

import cn.hutool.core.lang.Assert;
import dev.linqibin.patra.catalog.domain.model.enums.OrganizationNameType;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/// 机构名称值对象。
///
/// 字段映射：cat_organization_name 表
///
/// 基于 ROR Schema v2.0 的 names 字段定义。每个名称可以同时具有多个类型
/// （如 ror_display + label），并支持多语言。
///
/// **字段说明**：
///
/// | 字段 | 说明 |
/// |------|------|
/// | id | 数据库主键（雪花 ID，持久化后填充） |
/// | value | 名称文本 |
/// | types | 名称类型集合（可多选） |
/// | lang | ISO 639-1 语言代码（如 "en", "zh"） |
///
/// **业务规则**：
///
/// - 每个机构有且仅有一个 ROR_DISPLAY 类型的名称
/// - LABEL 表示机构官方名称，可能有多语言版本
/// - ALIAS 表示非官方名称、历史名称或翻译名称
/// - ACRONYM 表示缩写（如 MIT、NASA）
///
/// **相等性**：基于 value + lang，忽略 id 和 types
///
/// @param id 数据库主键（持久化后填充）
/// @param value 名称文本
/// @param types 名称类型集合
/// @param lang ISO 639-1 语言代码
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/fields#names">ROR Names Field</a>
@SuppressWarnings("java:S6218") // 自定义 equals/hashCode 基于业务语义（value + lang）
public record OrganizationName(Long id, String value, Set<OrganizationNameType> types, String lang)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// 紧凑构造器：验证参数。
  public OrganizationName {
    Assert.notBlank(value, "名称值不能为空");
    Assert.notEmpty(types, "名称类型不能为空");
    // 防御性拷贝，确保不可变
    types = Set.copyOf(types);
  }

  // ========== 工厂方法 ==========

  /// 创建机构名称（无 ID）。
  ///
  /// @param value 名称文本
  /// @param types 名称类型集合
  /// @param lang 语言代码
  /// @return 机构名称值对象
  public static OrganizationName create(
      String value, Set<OrganizationNameType> types, String lang) {
    return new OrganizationName(null, value, types, lang);
  }

  /// 创建带 ID 的机构名称（用于从数据库加载）。
  ///
  /// @param id 数据库主键
  /// @param value 名称文本
  /// @param types 名称类型集合
  /// @param lang 语言代码
  /// @return 机构名称值对象
  public static OrganizationName createWithId(
      Long id, String value, Set<OrganizationNameType> types, String lang) {
    return new OrganizationName(id, value, types, lang);
  }

  // ========== with-style 方法 ==========

  /// 添加 ID（返回新实例）。
  ///
  /// @param id 数据库主键
  /// @return 带 ID 的新实例
  public OrganizationName withId(Long id) {
    return new OrganizationName(id, this.value, this.types, this.lang);
  }

  // ========== 查询方法 ==========

  /// 判断是否已持久化（有 ID）。
  ///
  /// @return true 如果已持久化
  public boolean hasId() {
    return id != null;
  }

  /// 判断是否有语言代码。
  ///
  /// @return true 如果有语言代码
  public boolean hasLang() {
    return lang != null && !lang.isBlank();
  }

  /// 判断是否为 ROR 显示名。
  ///
  /// @return true 如果包含 ROR_DISPLAY 类型
  public boolean isDisplayName() {
    return types.contains(OrganizationNameType.ROR_DISPLAY);
  }

  /// 判断是否为官方名称（ROR 显示名或官方标签）。
  ///
  /// @return true 如果包含 ROR_DISPLAY 或 LABEL 类型
  public boolean isOfficialName() {
    return types.contains(OrganizationNameType.ROR_DISPLAY)
        || types.contains(OrganizationNameType.LABEL);
  }

  /// 判断是否为缩写。
  ///
  /// @return true 如果包含 ACRONYM 类型
  public boolean isAcronym() {
    return types.contains(OrganizationNameType.ACRONYM);
  }

  /// 判断是否为别名。
  ///
  /// @return true 如果包含 ALIAS 类型
  public boolean isAlias() {
    return types.contains(OrganizationNameType.ALIAS);
  }

  @Override
  public String toString() {
    return String.format("OrganizationName[value=%s, lang=%s, types=%s]", value, lang, types);
  }

  /// 业务相等性：value + lang。
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OrganizationName that)) {
      return false;
    }
    return Objects.equals(value, that.value) && Objects.equals(lang, that.lang);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, lang);
  }
}
