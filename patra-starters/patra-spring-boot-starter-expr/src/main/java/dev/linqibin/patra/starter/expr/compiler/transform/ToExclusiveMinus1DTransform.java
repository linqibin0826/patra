package dev.linqibin.patra.starter.expr.compiler.transform;

import dev.linqibin.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 排他性边界减1天变换:从日期值中减去一天,将排他性上界转换为包含性提供者边界。
///
/// 使用场景示例:PubMed 的 `maxdate` 是包含性的,但 std_key `to` 是排他性的。 此变换将 "2023-12-31" (排他) 转换为
/// "2023-12-30" (包含)。
///
/// 仅在日期粒度上操作 (YYYY-MM-DD 格式)。
///
/// 参见: docs/expr/03-compiler-bridge-internals.md §3.3.2, docs/expr/04-provider-pubmed.md §4.2
///
/// @since 0.1.0
public class ToExclusiveMinus1DTransform implements ValueTransform {

  private static final Logger log = LoggerFactory.getLogger(ToExclusiveMinus1DTransform.class);
  private static final String CODE = "TO_EXCLUSIVE_MINUS_1D";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

  @Override
  public String code() {
    return CODE;
  }

  @Override
  public String apply(String stdKey, String value, ProvenanceSnapshot snapshot) {
    if (value == null || value.isBlank()) {
      log.warn("TO_EXCLUSIVE_MINUS_1D: stdKey={} 收到 null 或空白值", stdKey);
      return value;
    }

    try {
      LocalDate date = LocalDate.parse(value, DATE_FORMATTER);
      LocalDate adjusted = date.minusDays(1);
      String result = adjusted.format(DATE_FORMATTER);

      log.debug("TO_EXCLUSIVE_MINUS_1D: stdKey={}, 原始值={}, 调整后={}", stdKey, value, result);
      return result;

    } catch (DateTimeParseException e) {
      log.error(
          "TO_EXCLUSIVE_MINUS_1D: 无法解析日期值 '{}',stdKey={}。期望 ISO_LOCAL_DATE 格式 (YYYY-MM-DD)。返回原始值。",
          value,
          stdKey,
          e);
      return value;
    }
  }
}
