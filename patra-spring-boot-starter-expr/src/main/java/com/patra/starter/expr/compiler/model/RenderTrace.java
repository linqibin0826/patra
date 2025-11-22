package com.patra.starter.expr.compiler.model;

import java.util.List;
import java.util.Objects;

/// 渲染跟踪,记录表达式渲染过程中命中的规则。
///
/// @param hits 命中列表
/// @author linqibin
/// @since 0.1.0
public record RenderTrace(List<Hit> hits) {
  public RenderTrace {
    hits = hits == null ? List.of() : List.copyOf(hits);
  }

  /// 规则命中记录。
  ///
  /// @param fieldKey 字段键
  /// @param op 操作符
  /// @param priority 优先级
  /// @param ruleId 规则ID
  public record Hit(String fieldKey, String op, int priority, String ruleId) {
    public Hit {
      Objects.requireNonNull(fieldKey, "fieldKey");
      Objects.requireNonNull(op, "op");
      Objects.requireNonNull(ruleId, "ruleId");
    }
  }
}
