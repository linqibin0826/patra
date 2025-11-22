package com.patra.starter.core.error.spi;

import java.util.Map;

/// SPI 接口,用于使用自定义字段丰富 {@link org.springframework.http.ProblemDetail} 响应。
///
/// 此 starter 不依赖 Web 技术栈;实现类应避免使用 servlet API。
///
/// 使用示例:
///
/// ```java
/// @Component
/// public class MyProblemFieldContributor implements ProblemFieldContributor {
///   @Override
///   public void contribute(Map<String, Object> fields, Throwable exception) {
///     fields.put("service", "patra-ingest");
///     fields.put("timestamp", Instant.now().toString());
/// ```
///
/// @author Patra Team
/// @since 2.0
public interface ProblemFieldContributor {

  /// 向 `ProblemDetail` 响应添加自定义扩展字段。
  ///
  /// @param fields 用于扩展属性的可变映射(永不为 `null`)
  /// @param exception 正在处理的异常(永不为 `null`)
  void contribute(Map<String, Object> fields, Throwable exception);
}
