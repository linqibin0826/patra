package dev.linqibin.patra.common.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/// 标识符类型枚举。
///
/// 用于标识不同来源的实体标识符，支持跨服务共享使用。
/// 可用于文献标识符、作者标识符、机构标识符等场景。
///
/// **文献标识符**：
///
/// - **PMID** - PubMed ID（如 "38123456"）
/// - **DOI** - 数字对象标识符（如 "10.1038/nature12345"）
/// - **PMC** - PubMed Central ID（如 "PMC1234567"）
/// - **PII** - Publisher Item Identifier（出版商内部标识）
/// - **ARXIV** - arXiv 预印本标识符
///
/// **人员标识符**：
///
/// - **ORCID** - 研究者唯一标识符（如 "0000-0001-2345-6789"）
///
/// **机构标识符**：
///
/// - **ROR** - Research Organization Registry ID（如 "03vek6s52"）
/// - **RINGGOLD** - Ringgold ID（机构标识符）
/// - **GRID** - Global Research Identifier Database（已弃用，迁移至 ROR）
/// - **ISNI** - 国际标准名称标识符
///
/// **兜底类型**：
///
/// - **OTHER** - 其他未知标识符类型
///
/// @author linqibin
/// @since 0.1.0
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

  /// ORCID - 研究者唯一标识符
  ORCID("orcid", "ORCID iD"),

  /// ROR - Research Organization Registry ID（机构标识符）
  ROR("ror", "Research Organization Registry ID"),

  /// Ringgold ID - 机构标识符（商业数据库）
  RINGGOLD("ringgold", "Ringgold ID"),

  /// GRID - Global Research Identifier Database（已弃用）
  GRID("grid", "Global Research Identifier Database"),

  /// ISNI - 国际标准名称标识符
  ISNI("isni", "International Standard Name Identifier"),

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

  /// 获取代码值（用于 JSON 序列化）。
  ///
  /// @return 代码值（小写）
  @JsonValue
  public String getCode() {
    return code;
  }

  /// 获取描述文本。
  ///
  /// @return 描述文本
  public String getDescription() {
    return description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "pmid", "DOI", "pmc"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static PublicationIdentifierType fromCode(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("标识符类型代码不能为空");
    }
    String normalized = value.trim().toLowerCase();
    for (PublicationIdentifierType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的标识符类型：" + value);
  }

  /// 从代码值解析枚举（不区分大小写），未知类型返回 OTHER。
  ///
  /// 此方法同时用于 JSON 反序列化，未知类型不会抛出异常而是返回 OTHER。
  ///
  /// @param value 代码值
  /// @return 对应的枚举值，未知类型返回 OTHER
  @JsonCreator
  public static PublicationIdentifierType fromCodeOrOther(String value) {
    if (value == null || value.isBlank()) {
      return OTHER;
    }
    String normalized = value.trim().toLowerCase();
    for (PublicationIdentifierType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    return OTHER;
  }

  /// 判断是否为主要标识符类型（PMID 或 DOI）。
  ///
  /// @return true 如果为 PMID 或 DOI
  public boolean isPrimary() {
    return this == PMID || this == DOI;
  }
}
