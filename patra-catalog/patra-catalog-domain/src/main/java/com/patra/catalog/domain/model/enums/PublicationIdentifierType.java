package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 文献标识符类型枚举。
///
/// 字段映射：cat_publication_identifier.type
///
/// 类型说明：
///
/// - **PMID** - PubMed ID（如 "38123456"）
/// - **DOI** - 数字对象标识符（如 "10.1038/nature12345"）
/// - **PMC** - PubMed Central ID（如 "PMC1234567"）
/// - **PII** - Publisher Item Identifier（出版商内部标识）
/// - **ARXIV** - arXiv 预印本标识符
/// - **OTHER** - 其他标识符类型
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum PublicationIdentifierType {

  /// PubMed ID - NLM 分配的唯一标识符
  PMID("pmid", "PubMed ID"),

  /// DOI - 数字对象标识符
  DOI("doi", "Digital Object Identifier"),

  /// PMC - PubMed Central ID
  PMC("pmc", "PubMed Central ID"),

  /// PII - 出版商内部标识符
  PII("pii", "Publisher Item Identifier"),

  /// arXiv - 预印本标识符
  ARXIV("arxiv", "arXiv ID"),

  /// 其他标识符类型
  OTHER("other", "Other Identifier");

  /// 数据库存储的代码值（小写）
  private final String code;

  /// 描述文本
  private final String description;

  PublicationIdentifierType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "pmid", "DOI", "pmc"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static PublicationIdentifierType fromCode(String value) {
    Assert.notBlank(value, "标识符类型代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (PublicationIdentifierType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的标识符类型：" + value);
  }

  /// 判断是否为主要标识符类型（PMID 或 DOI）。
  ///
  /// @return true 如果为 PMID 或 DOI
  public boolean isPrimary() {
    return this == PMID || this == DOI;
  }
}
