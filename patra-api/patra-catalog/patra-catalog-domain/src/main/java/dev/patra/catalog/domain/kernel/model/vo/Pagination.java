package dev.patra.catalog.domain.kernel.model.vo;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 文献页码：PubMed 的 `MedlinePgn`（NLM 缩写形式，如 `1451-61`）与较新记录才有的 `StartPage` / `EndPage`。
///
/// 引用文本由 CSL 样式按 `page-range-format` 自行展开 `MedlinePgn`；RIS、CSV 要分开的首页与尾页，由
/// [#firstRange] 推导。
///
/// @param medlinePgn `MedlinePgn` 原文，可空
/// @param startPage `StartPage`，可空
/// @param endPage `EndPage`，可空
/// @author linqibin
/// @since 0.1.0
public record Pagination(String medlinePgn, String startPage, String endPage) {

  /// `前缀字母* 数字+ 后缀字母* - 前缀字母* 数字+ 后缀字母*`。
  private static final Pattern RANGE =
      Pattern.compile("^([A-Za-z]*)(\\d+)([A-Za-z]*)-([A-Za-z]*)(\\d+)([A-Za-z]*)$");

  /// 创建页码。
  ///
  /// @param medlinePgn `MedlinePgn` 原文，可空
  /// @param startPage `StartPage`，可空
  /// @param endPage `EndPage`，可空
  /// @return 页码
  public static Pagination of(String medlinePgn, String startPage, String endPage) {
    return new Pagination(medlinePgn, startPage, endPage);
  }

  /// 是否一个页码都没有。
  ///
  /// @return 三项都为空或空白时为 `true`
  public boolean isEmpty() {
    return isBlank(medlinePgn) && isBlank(startPage) && isBlank(endPage);
  }

  /// 第一段页码。
  ///
  /// 1. 有 `StartPage`：首页 = `StartPage`，尾页 = `EndPage`；
  /// 2. 否则取 `MedlinePgn` 第一个以 `;` 或 `,` 分隔的片段，形如 `A-B` 时尾页比首页短则按首页补齐高位；
  /// 3. 都不匹配：首页 = 整个片段，尾页为空。
  ///
  /// @return 页码段；一个页码都没有时为空
  public Optional<PageRange> firstRange() {
    if (!isBlank(startPage)) {
      return Optional.of(PageRange.of(startPage.trim(), isBlank(endPage) ? null : endPage.trim()));
    }
    if (isBlank(medlinePgn)) {
      return Optional.empty();
    }
    String segment = medlinePgn.split("[;,]", 2)[0].trim();
    Matcher m = RANGE.matcher(segment);
    if (!m.matches()) {
      return Optional.of(PageRange.of(segment, null));
    }
    String firstDigits = m.group(2);
    String lastDigits = m.group(5);
    if (lastDigits.length() < firstDigits.length()) {
      lastDigits =
          firstDigits.substring(0, firstDigits.length() - lastDigits.length()) + lastDigits;
    }
    return Optional.of(
        PageRange.of(m.group(1) + firstDigits + m.group(3), m.group(4) + lastDigits + m.group(6)));
  }

  /// 是否为空或空白。
  ///
  /// @param value 字符串，可空
  /// @return 为 null 或只含空白时为 `true`
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
