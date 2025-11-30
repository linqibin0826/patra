package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// MeSH 入口术语词法标记枚举。
///
/// 字段映射：cat_mesh_entry_term.lexical_tag
///
/// 类型说明（参考 [NLM MeSH XML Data Elements](https://www.nlm.nih.gov/mesh/xml_data_elements.html)）：
///
/// - **NON**：无特殊标记(None)
/// - **PEF**：首选术语(Preferred Term)
/// - **LAB**：实验室编号(Lab Number)
/// - **ABB**：缩写(Abbreviation)
/// - **ABX**：嵌入式缩写(Embedded Abbreviation)
/// - **ACR**：首字母缩写(Acronym)
/// - **ACX**：嵌入式首字母缩写(Embedded Acronym)
/// - **NAM**：专有名词(Proper Name)
/// - **EPO**：人名术语(Eponym)
/// - **TRD**：商标名(Trade Name)
/// - **HIST**：历史术语(Historical Term)
///
/// 业务规则：
///
/// - PEF 标记的术语是该主题词的首选名称
/// - ABB/ACR/ABX/ACX 标记的术语在检索时需要特殊处理
/// - TRD 标记的术语为商标/品牌名称
/// - EPO 标记的术语来源于人名（如 Alzheimer）
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
  NAM("NAM", "Named Entity", "命名实体"),

  /// 嵌入式缩写
  ABX("ABX", "Embedded Abbreviation", "嵌入式缩写"),

  /// 嵌入式首字母缩写
  ACX("ACX", "Embedded Acronym", "嵌入式首字母缩写"),

  /// 人名术语
  EPO("EPO", "Eponym", "人名术语"),

  /// 商标名
  TRD("TRD", "Trade Name", "商标名"),

  /// 历史术语
  HIST("HIST", "Historical Term", "历史术语");

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
  /// @return true 如果为缩写或首字母缩写（包括嵌入式）
  public boolean isAbbreviation() {
    return this == ABB || this == ACR || this == ABX || this == ACX;
  }
}
