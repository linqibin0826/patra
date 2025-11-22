package com.patra.catalog.domain.model.enums;

import cn.hutool.core.lang.Assert;
import lombok.Getter;

/// 文献出版状态枚举。
///
/// 字段映射：cat_publication.publication_status
///
/// 状态说明：
///
/// - **PPUBLISH** - 纸质出版（Print Published）
///   - **EPUBLISH** - 电子出版（Electronic Published）
///   - **AHEADOFPRINT** - 预印版（Ahead of Print）
///   - **PUBMED** - 已收录到 PubMed
///   - **PUBMEDNOTMEDLINE** - 收录到 PubMed 但未收录到 MEDLINE
///   - **PREMEDLINE** - MEDLINE 预处理状态
///
/// @author linqibin
/// @since 0.1.0
@Getter
public enum PublicationStatus {

  /// 纸质出版
  PPUBLISH("ppublish", "Print Published"),

  /// 电子出版
  EPUBLISH("epublish", "Electronic Published"),

  /// 预印版
  AHEADOFPRINT("aheadofprint", "Ahead of Print"),

  /// 已收录到 PubMed
  PUBMED("pubmed", "PubMed"),

  /// 收录到 PubMed 但未收录到 MEDLINE
  PUBMEDNOTMEDLINE("pubmednotmedline", "PubMed Not MEDLINE"),

  /// MEDLINE 预处理状态
  PREMEDLINE("premedline", "Pre-MEDLINE");

  private final String code;
  private final String description;

  PublicationStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /// 从代码值解析枚举（不区分大小写）。
  ///
  /// @param value 代码值（如 "ppublish", "EPUBLISH", "aheadofprint"）
  /// @return 对应的枚举值
  /// @throws IllegalArgumentException 如果代码值无效
  public static PublicationStatus fromCode(String value) {
    Assert.notBlank(value, "出版状态代码不能为空");
    String normalized = value.trim().toLowerCase();
    for (PublicationStatus status : values()) {
      if (status.code.equals(normalized)) {
        return status;
      }
    }
    throw new IllegalArgumentException("未知的出版状态：" + value);
  }
}
