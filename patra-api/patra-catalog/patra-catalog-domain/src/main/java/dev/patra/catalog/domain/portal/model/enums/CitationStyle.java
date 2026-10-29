package dev.patra.catalog.domain.portal.model.enums;

import java.util.Arrays;

/// 复制引用支持的引用样式，`code` 是接口里的取值。
///
/// @author linqibin
/// @since 0.1.0
public enum CitationStyle {
  /// GB/T 7714-2015 顺序编码制。
  GB_T_7714_2015("gb-t-7714-2015"),
  /// Vancouver（NLM Citing Medicine）。
  VANCOUVER("vancouver"),
  /// APA 第 7 版。
  APA_7("apa-7"),
  /// AMA Manual of Style 第 11 版。
  AMA_11("ama-11");

  private final String code;

  /// 创建样式。
  ///
  /// @param code 接口代码
  CitationStyle(String code) {
    this.code = code;
  }

  /// 接口代码。
  ///
  /// @return 代码（如 `gb-t-7714-2015`）
  public String code() {
    return code;
  }

  /// 按接口代码找样式。
  ///
  /// @param code 代码
  /// @return 样式
  /// @throws IllegalArgumentException 未知代码时
  public static CitationStyle fromCode(String code) {
    return Arrays.stream(values())
        .filter(s -> s.code.equals(code))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未知的引用样式：" + code));
  }
}
