package dev.patra.catalog.domain.portal.model.enums;

import java.util.Arrays;

/// 导出格式：接口代码、响应 Content-Type 与文件扩展名（spec 第 6.1 节）。
///
/// @author linqibin
/// @since 0.1.0
public enum ExportFormat {
  /// RIS。
  RIS("ris", "application/x-research-info-systems; charset=UTF-8", "ris"),
  /// BibTeX。
  BIBTEX("bibtex", "application/x-bibtex; charset=UTF-8", "bib"),
  /// CSV（UTF-8 带 BOM）。
  CSV("csv", "text/csv; charset=UTF-8", "csv"),
  /// CSL-JSON。
  CSL_JSON("csl-json", "application/vnd.citationstyles.csl+json", "json");

  private final String code;
  private final String contentType;
  private final String extension;

  /// 创建格式。
  ///
  /// @param code 接口代码
  /// @param contentType 响应 Content-Type
  /// @param extension 文件扩展名（不含点）
  ExportFormat(String code, String contentType, String extension) {
    this.code = code;
    this.contentType = contentType;
    this.extension = extension;
  }

  /// 接口代码。
  ///
  /// @return 代码（如 `csl-json`）
  public String code() {
    return code;
  }

  /// 响应 Content-Type。
  ///
  /// @return Content-Type
  public String contentType() {
    return contentType;
  }

  /// 文件扩展名。
  ///
  /// @return 扩展名（不含点）
  public String extension() {
    return extension;
  }

  /// 按接口代码找格式。
  ///
  /// @param code 代码
  /// @return 格式
  /// @throws IllegalArgumentException 未知代码时
  public static ExportFormat fromCode(String code) {
    return Arrays.stream(values())
        .filter(f -> f.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知的导出格式：" + code));
  }
}
