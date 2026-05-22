package dev.linqibin.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import java.util.Locale;
import lombok.Getter;

/// 翻译类型枚举。
///
/// 字段映射：cat_publication_alternative_abstract.translation_type
///
/// 翻译来源类型说明：
///
/// - **OFFICIAL** - 官方翻译（出版商或作者提供）
/// - **PROFESSIONAL** - 专业翻译（专业翻译机构或人员）
/// - **MACHINE** - 机器翻译（自动翻译系统，如 DeepL、Google Translate）
/// - **COMMUNITY** - 社区翻译（志愿者或用户贡献）
///
/// 可信度排序：OFFICIAL > PROFESSIONAL > COMMUNITY > MACHINE
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum TranslationType {

  /// 官方翻译 - 出版商或作者提供
  OFFICIAL("official", "Official Translation", 100),

  /// 专业翻译 - 翻译机构或专业译者
  PROFESSIONAL("professional", "Professional Translation", 80),

  /// 社区翻译 - 志愿者或用户贡献
  COMMUNITY("community", "Community Translation", 50),

  /// 机器翻译 - 自动翻译系统
  MACHINE("machine", "Machine Translation", 30);

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  /// 可信度分数（数值越大可信度越高）
  private final int trustScore;

  TranslationType(String code, String description, int trustScore) {
    this.code = code;
    this.description = description;
    this.trustScore = trustScore;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "official", "MACHINE"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static TranslationType fromCode(String value) {
    Assert.notBlank(value, "翻译类型代码不能为空");
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (TranslationType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的翻译类型：" + value);
  }

  /// 判断是否为人工翻译。
  ///
  /// @return true 如果不是机器翻译
  public boolean isHumanTranslated() {
    return this != MACHINE;
  }

  /// 判断可信度是否高于指定类型。
  ///
  /// @param other 比较的翻译类型
  /// @return true 如果当前类型可信度更高
  public boolean isMoreTrustedThan(TranslationType other) {
    return this.trustScore > other.trustScore;
  }
}
