package dev.patra.catalog.adapter.portal.rest;

import dev.patra.catalog.domain.portal.model.enums.ExportFormat;
import java.time.LocalDate;
import java.util.regex.Pattern;

/// 导出文件名（spec 第 6.1 节）：`patra-<name>-<YYYY-MM-DD>.<ext>`。
///
/// `name` 去掉 `\ / : * ? " < > |` 与控制字符，空白折叠成 `-`；去完为空时用 `export`。
///
/// @author linqibin
/// @since 0.1.0
final class ExportFileNames {

  private static final Pattern ILLEGAL = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}&&[^\\s]]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final String FALLBACK = "export";

  private ExportFileNames() {}

  /// 生成文件名。
  ///
  /// @param name 用户给的名字，可空
  /// @param format 导出格式（决定扩展名）
  /// @param date 导出日期
  /// @return 文件名
  static String of(String name, ExportFormat format, LocalDate date) {
    String cleaned = name == null ? "" : ILLEGAL.matcher(name).replaceAll("");
    cleaned = WHITESPACE.matcher(cleaned.strip()).replaceAll("-");
    if (cleaned.isEmpty()) {
      cleaned = FALLBACK;
    }
    return "patra-" + cleaned + "-" + date + "." + format.extension();
  }
}
