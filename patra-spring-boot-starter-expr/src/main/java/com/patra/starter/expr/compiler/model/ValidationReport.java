package com.patra.starter.expr.compiler.model;

import java.util.Collections;
import java.util.List;

/**
 * 验证报告,包含警告和错误列表。
 *
 * @param warnings 警告列表
 * @param errors 错误列表
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationReport(List<Issue> warnings, List<Issue> errors) {
  public ValidationReport {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    errors = errors == null ? List.of() : List.copyOf(errors);
  }

  /**
   * 检查验证是否通过(无错误)。
   *
   * @return 如果无错误则返回 true
   */
  public boolean ok() {
    return errors.isEmpty();
  }

  /**
   * 创建空的验证报告。
   *
   * @return 空报告
   */
  public static ValidationReport empty() {
    return new ValidationReport(Collections.emptyList(), Collections.emptyList());
  }
}
