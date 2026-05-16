package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 机构类型枚举。
///
/// 字段映射：cat_organization.types (JSON 数组)
///
/// 基于 ROR (Research Organization Registry) Schema v2.0 定义的机构分类。
/// ROR 机构可同时属于多个类型（如大学既是 education 也可能是 healthcare）。
///
/// **类型说明**：
///
/// | 类型 | 说明 | 示例 |
/// |------|------|------|
/// | ARCHIVE | 档案馆、图书馆 | 国家图书馆、数字档案馆 |
/// | COMPANY | 企业机构 | 制药公司、生物技术公司 |
/// | EDUCATION | 教育机构 | 大学、研究所、学院 |
/// | FACILITY | 科研设施 | 大型科学装置、实验室 |
/// | FUNDER | 资助机构 | 基金会、科研资助机构 |
/// | GOVERNMENT | 政府机构 | 卫生部、科技部 |
/// | HEALTHCARE | 医疗机构 | 医院、诊所、医疗中心 |
/// | NONPROFIT | 非营利组织 | 学术协会、慈善基金会 |
/// | OTHER | 其他类型 | 无法归类的机构 |
///
/// **使用示例**：
///
/// ```java
/// // 从 ROR JSON 解析
/// OrganizationType type = OrganizationType.fromCode("education");
///
/// // 类型判断
/// if (type.isResearchRelated()) {
///     // 处理研究相关机构
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://ror.readme.io/docs/data-structure">ROR Data Structure</a>
@Getter
public enum OrganizationType {

  /// 档案馆、图书馆
  ARCHIVE("archive", "档案馆"),

  /// 企业机构
  COMPANY("company", "企业"),

  /// 教育机构
  EDUCATION("education", "教育机构"),

  /// 科研设施
  FACILITY("facility", "设施"),

  /// 资助机构
  FUNDER("funder", "资助机构"),

  /// 政府机构
  GOVERNMENT("government", "政府机构"),

  /// 医疗机构
  HEALTHCARE("healthcare", "医疗机构"),

  /// 非营利组织
  NONPROFIT("nonprofit", "非营利组织"),

  /// 其他类型
  OTHER("other", "其他");

  /// 数据库存储的代码值（与 ROR 一致，小写）
  private final String code;

  /// 中文描述
  private final String description;

  OrganizationType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "education", "HEALTHCARE"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值为空或无效
  public static OrganizationType fromCode(String value) {
    Assert.notBlank(value, "机构类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (OrganizationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的机构类型：" + value);
  }

  /// 尝试从代码值解析枚举，如果无法识别则返回 null。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，无法识别则返回 null
  public static OrganizationType fromCodeOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().toLowerCase();
    for (OrganizationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return null;
  }

  /// 判断是否为教育机构。
  ///
  /// @return true 如果为教育机构
  public boolean isEducation() {
    return this == EDUCATION;
  }

  /// 判断是否为医疗机构。
  ///
  /// @return true 如果为医疗机构
  public boolean isHealthcare() {
    return this == HEALTHCARE;
  }

  /// 判断是否为企业机构。
  ///
  /// @return true 如果为企业机构
  public boolean isCompany() {
    return this == COMPANY;
  }

  /// 判断是否为资助机构。
  ///
  /// @return true 如果为资助机构
  public boolean isFunder() {
    return this == FUNDER;
  }

  /// 判断是否为研究相关机构。
  ///
  /// 教育机构、医疗机构、科研设施、资助机构通常与学术研究直接相关。
  ///
  /// @return true 如果为研究相关机构
  public boolean isResearchRelated() {
    return this == EDUCATION || this == HEALTHCARE || this == FACILITY || this == FUNDER;
  }
}
