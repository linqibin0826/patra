package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// MeSH 主题词类型枚举。
///
/// 字段映射：cat_mesh_descriptor.descriptor_class → 1/2/3/4
///
/// 类型说明：
///
/// - **TOPICAL**：主题词(Topical Descriptor,如 "Antibodies", "Diabetes Mellitus")
///   - **PUBLICATION_TYPE**：出版类型(Publication Type,如 "Review", "Clinical Trial")
///   - **GEOGRAPHICALS**：地理名称(Geographicals,如 "United States", "China")
///   - **CHECK_TAG**：检查标签(Check Tag,如 "Male", "Female", "Human")
///
/// 业务规则：
///
/// - TOPICAL 是最常见的类型,约占 95%
///   - CHECK_TAG 用于标记性别、年龄、人/动物等基本特征
///   - 不同类型的主题词在检索时的权重不同
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum DescriptorClass {

  /// 主题词(95%的主题词属于此类型)
  TOPICAL("1", "Topical Descriptor", "主题词"),

  /// 出版类型
  PUBLICATION_TYPE("2", "Publication Type", "出版类型"),

  /// 地理名称
  GEOGRAPHICALS("3", "Geographicals", "地理名称"),

  /// 检查标签(性别/年龄/人/动物等基本特征)
  CHECK_TAG("4", "Check Tag", "检查标签");

  /// 数据库存储的代码值
  private final String code;

  /// 英文描述
  private final String description;

  /// 中文描述
  private final String descriptionZh;

  DescriptorClass(String code, String description, String descriptionZh) {
    this.code = code;
    this.description = description;
    this.descriptionZh = descriptionZh;
  }

  /// 从代码值解析枚举。
  ///
  /// @param value 代码值(如 "1", "2")
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static DescriptorClass fromCode(String value) {
    Assert.notBlank(value, "主题词类型代码不能为空");
    String normalized = value.trim();
    for (DescriptorClass type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的主题词类型：" + value);
  }

  /// 判断是否为主题词。
  ///
  /// @return true 如果为主题词类型
  public boolean isTopical() {
    return this == TOPICAL;
  }

  /// 判断是否为出版类型。
  ///
  /// @return true 如果为出版类型
  public boolean isPublicationType() {
    return this == PUBLICATION_TYPE;
  }

  /// 判断是否为检查标签。
  ///
  /// @return true 如果为检查标签
  public boolean isCheckTag() {
    return this == CHECK_TAG;
  }
}
