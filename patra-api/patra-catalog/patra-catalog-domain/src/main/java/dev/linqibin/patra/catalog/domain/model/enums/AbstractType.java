package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.util.Locale;
import lombok.Getter;

/// 摘要类型枚举。
///
/// 字段映射：cat_publication_abstract.abstract_type
///
/// 类型说明：
///
/// - **STRUCTURED** - 结构化摘要（含 BACKGROUND/METHODS/RESULTS/CONCLUSIONS 等段落）
/// - **UNSTRUCTURED** - 非结构化摘要（纯文本段落）
/// - **GRAPHICAL** - 图形化摘要
/// - **NONE** - 无摘要
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum AbstractType {

  /// 结构化摘要 - 含多个命名段落
  STRUCTURED("structured", "Structured Abstract"),

  /// 非结构化摘要 - 纯文本
  UNSTRUCTURED("unstructured", "Unstructured Abstract"),

  /// 图形化摘要
  GRAPHICAL("graphical", "Graphical Abstract"),

  /// 无摘要
  NONE("none", "No Abstract");

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  AbstractType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "structured", "UNSTRUCTURED"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static AbstractType fromCode(String value) {
    Assert.notBlank(value, "摘要类型代码不能为空");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (AbstractType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的摘要类型：" + value);
  }

  /// 判断是否有实际内容。
  ///
  /// @return true 如果不是 NONE
  public boolean hasContent() {
    return this != NONE;
  }
}
