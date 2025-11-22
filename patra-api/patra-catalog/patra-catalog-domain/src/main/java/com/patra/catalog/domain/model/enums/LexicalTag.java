package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// MeSH 入口术语词法标记枚举。
/// 
/// 字段映射：cat_mesh_entry_term.lexical_tag → NON/PEF/LAB/ABB/ACR/NAM
/// 
/// 类型说明：
/// 
/// - **NON**：无特殊标记(None)
///   - **PEF**：首选术语(Preferred Term)
///   - **LAB**：实验室术语(Laboratory Term)
///   - **ABB**：缩写(Abbreviation)
///   - **ACR**：首字母缩写(Acronym)
///   - **NAM**：命名(Named Entity)
/// 
/// 业务规则：
/// 
/// - PEF 标记的术语是该主题词的首选名称
///   - ABB/ACR 标记的术语在检索时需要特殊处理
///   - LAB 标记的术语常用于实验室场景
/// 
/// @author linqibin
/// @since 0.1.0
@Getter
public enum LexicalTag {

  /// 无特殊标记
  NON("NON", "None", "无标记"),

  /// 首选术语
  PEF("PEF", "Preferred Term", "首选术语"),

  /// 实验室术语
  LAB("LAB", "Laboratory Term", "实验室术语"),

  /// 缩写
  ABB("ABB", "Abbreviation", "缩写"),

  /// 首字母缩写
  ACR("ACR", "Acronym", "首字母缩写"),

  /// 命名实体
  NAM("NAM", "Named Entity", "命名实体");

  /// 数据库存储的代码值
  private final String code;

  /// 英文描述
  private final String description;

  /// 中文描述
  private final String descriptionZh;

  LexicalTag(String code, String description, String descriptionZh) {
    this.code = code;
    this.description = description;
    this.descriptionZh = descriptionZh;
  }

  /// 从代码值解析枚举。
/// 
/// @param value 代码值(如 "PEF", "ABB")
/// @return 对应的枚举值
/// @throws IllegalArgumentException 如果代码值无效
  public static LexicalTag fromCode(String value) {
    Assert.notBlank(value, "词法标记代码不能为空");
    String normalized = value.trim().toUpperCase();
    for (LexicalTag tag : values()) {
      if (tag.code.equals(normalized)) {
        return tag;
      }
    }
    throw new IllegalArgumentException("未知的词法标记：" + value);
  }

  /// 判断是否为首选术语。
/// 
/// @return true 如果为首选术语
  public boolean isPreferred() {
    return this == PEF;
  }

  /// 判断是否为缩写类型。
/// 
/// @return true 如果为缩写或首字母缩写
  public boolean isAbbreviation() {
    return this == ABB || this == ACR;
  }
}
