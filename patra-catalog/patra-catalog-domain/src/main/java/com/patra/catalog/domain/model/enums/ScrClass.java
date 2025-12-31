package com.patra.catalog.domain.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// SCR（Supplementary Concept Record）类别枚举。
///
/// 定义 SCR 的 6 种类别，每种类别映射到不同的主题词树：
///
/// - **CHEMICAL(1)**：化学物质，映射到 D 树（药物和化学物质）
/// - **PROTOCOL(2)**：化疗方案，映射到"抗肿瘤联合化疗方案"及相关化学物质
/// - **DISEASE(3)**：疾病，映射到 C 树（疾病）和 A 树（解剖结构）
/// - **ORGANISM(4)**：生物体（如病毒），映射到 B 树（生物体），2018年新增
/// - **POPULATION_GROUP(5)**：人群组（如民族、部落），映射到 M 树，2023年新增
/// - **ANATOMY(6)**：解剖结构（如菱形唇），映射到 A 树
///
/// 业务规则：
///
/// - 默认类别为 CHEMICAL(1)
/// - DTD 中定义：SCRClass (1 | 2 | 3 | 4 | 5 | 6) "1"
///
/// @author linqibin
/// @since 0.1.0
/// @see <a href="https://www.nlm.nih.gov/mesh/intro_record_types.html">MeSH Record Types</a>
@Getter
@RequiredArgsConstructor
public enum ScrClass {

  /// 化学物质 - 映射到 D 树（药物和化学物质）。
  CHEMICAL(1, "化学物质"),

  /// 化疗方案 - 专门用于抗肿瘤联合化疗方案。
  PROTOCOL(2, "化疗方案"),

  /// 疾病 - 映射到 C 树（疾病）和 A 树（解剖结构）。
  DISEASE(3, "疾病"),

  /// 生物体 - 映射到 B 树（生物体），如病毒。2018年新增。
  ORGANISM(4, "生物体"),

  /// 人群组 - 映射到 M 树，如民族、部落名称。2023年新增。
  POPULATION_GROUP(5, "人群组"),

  /// 解剖结构 - 映射到 A 树。
  ANATOMY(6, "解剖结构");

  /// 类别代码（1-6）。
  private final int code;

  /// 类别中文名（用于日志和用户展示）。
  private final String displayName;

  /// 根据 code 查找枚举。
  ///
  /// @param code 类别代码（1-6）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果 code 不在 1-6 范围内
  public static ScrClass fromCode(int code) {
    for (ScrClass scrClass : values()) {
      if (scrClass.code == code) {
        return scrClass;
      }
    }
    throw new IllegalArgumentException("未知的 SCR 类别: " + code);
  }

  /// 根据 code 查找枚举，如果为 null 则返回默认值 CHEMICAL。
  ///
  /// @param code 类别代码（可为 null）
  /// @return 对应的枚举值，null 时返回 CHEMICAL
  public static ScrClass fromCodeOrDefault(Integer code) {
    if (code == null) {
      return CHEMICAL;
    }
    return fromCode(code);
  }

  /// 从字符串格式的 code 解析枚举。
  ///
  /// @param codeStr 类别代码字符串（如 "1", "2"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果字符串格式无效或 code 不在范围内
  public static ScrClass fromCodeString(String codeStr) {
    try {
      int code = Integer.parseInt(codeStr);
      return fromCode(code);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("无效的 SCR 类别字符串: " + codeStr, e);
    }
  }
}
