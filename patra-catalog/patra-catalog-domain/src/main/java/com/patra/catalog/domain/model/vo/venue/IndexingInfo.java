package com.patra.catalog.domain.model.vo.venue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serial;
import java.io.Serializable;

/// MEDLINE 索引收录信息值对象。封装期刊在 MEDLINE/PubMed 的收录状态和缩写标题。
///
/// 设计原则：
///
/// - 不可变性：Record 自动提供
/// - PubMed 专有：数据主要来自 PubMed Catalog
/// - 可选性：并非所有期刊都被 MEDLINE 收录
///
/// MEDLINE 收录状态说明：
///
/// | 状态 | 说明 |
/// |------|------|
/// | `C` | Currently indexed for MEDLINE |
/// | `Y` | Currently indexed (subset) |
/// | `N` | Not currently indexed |
/// | `D` | Discontinued (已停止收录) |
///
/// 数据来源：
///
/// PubMed Catalog 的 `CurrentIndexingStatus`、`MedlineTA`、`ISOAbbreviation` 字段。
///
/// 使用示例：
///
/// ```java
/// // MEDLINE 收录的期刊
/// IndexingInfo indexed = IndexingInfo.of("C", "Nature", "Nature");
///
/// // 仅状态已知
/// IndexingInfo statusOnly = IndexingInfo.ofStatus("N");
///
/// // 完整信息
/// IndexingInfo full = IndexingInfo.of("C", "N Engl J Med", "N. Engl. J. Med.");
/// ```
///
/// @param status MEDLINE 收录状态（C/Y/N/D）
/// @param medlineTa MEDLINE 缩写标题（Title Abbreviation）
/// @param isoAbbreviation ISO 缩写标题
/// @author linqibin
/// @since 0.1.0
@JsonIgnoreProperties(ignoreUnknown = true)
public record IndexingInfo(String status, String medlineTa, String isoAbbreviation)
    implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /// MEDLINE 收录状态常量：当前被 MEDLINE 索引。
  public static final String STATUS_CURRENTLY_INDEXED = "C";

  /// MEDLINE 收录状态常量：当前被索引（子集）。
  public static final String STATUS_INDEXED_SUBSET = "Y";

  /// MEDLINE 收录状态常量：当前未被索引。
  public static final String STATUS_NOT_INDEXED = "N";

  /// MEDLINE 收录状态常量：已停止收录。
  public static final String STATUS_DISCONTINUED = "D";

  /// 创建索引收录信息。
  ///
  /// @param status MEDLINE 收录状态
  /// @param medlineTa MEDLINE 缩写标题
  /// @param isoAbbreviation ISO 缩写标题
  /// @return 索引收录信息值对象
  public static IndexingInfo of(String status, String medlineTa, String isoAbbreviation) {
    return new IndexingInfo(status, medlineTa, isoAbbreviation);
  }

  /// 创建仅包含状态的索引收录信息。
  ///
  /// @param status MEDLINE 收录状态
  /// @return 索引收录信息值对象
  public static IndexingInfo ofStatus(String status) {
    return new IndexingInfo(status, null, null);
  }

  /// 判断期刊是否当前被 MEDLINE 收录。
  ///
  /// @return true 如果当前被 MEDLINE 索引（状态为 C 或 Y）
  @JsonIgnore
  public boolean isCurrentlyIndexed() {
    return STATUS_CURRENTLY_INDEXED.equals(status) || STATUS_INDEXED_SUBSET.equals(status);
  }

  /// 判断期刊是否曾被 MEDLINE 收录但已停止。
  ///
  /// @return true 如果状态为已停止收录
  @JsonIgnore
  public boolean isDiscontinued() {
    return STATUS_DISCONTINUED.equals(status);
  }

  /// 判断期刊是否从未被 MEDLINE 收录。
  ///
  /// @return true 如果从未被收录
  @JsonIgnore
  public boolean isNeverIndexed() {
    return STATUS_NOT_INDEXED.equals(status);
  }

  /// 判断是否有 MEDLINE 缩写标题。
  ///
  /// @return true 如果有 MEDLINE 缩写标题
  public boolean hasMedlineTa() {
    return medlineTa != null && !medlineTa.isBlank();
  }

  /// 判断是否有 ISO 缩写标题。
  ///
  /// @return true 如果有 ISO 缩写标题
  public boolean hasIsoAbbreviation() {
    return isoAbbreviation != null && !isoAbbreviation.isBlank();
  }

  /// 获取首选缩写标题（优先 MEDLINE，其次 ISO）。
  ///
  /// @return 首选缩写标题，如果都没有则返回 null
  @JsonIgnore
  public String getPreferredAbbreviation() {
    if (hasMedlineTa()) {
      return medlineTa;
    }
    if (hasIsoAbbreviation()) {
      return isoAbbreviation;
    }
    return null;
  }
}
