package dev.linqibin.commons.error.field;

import java.util.List;

/// 带字段错误的异常实现此接口，统一错误格式会把它们输出成 `errors[]`。
public interface HasFieldViolations {

  /// 返回这个异常携带的字段错误。
  ///
  /// @return 字段错误列表，不为 `null`
  List<FieldViolation> getFieldViolations();
}
