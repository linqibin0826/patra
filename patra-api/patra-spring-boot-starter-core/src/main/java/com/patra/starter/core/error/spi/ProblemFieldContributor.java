package com.patra.starter.core.error.spi;

import java.util.Map;

/**
 * SPI 接口,用于使用自定义字段丰富 {@link org.springframework.http.ProblemDetail} 响应。
 *
 * <p>此 starter 不依赖 Web 技术栈;实现类应避免使用 servlet API。
 *
 * <p>使用示例:
 *
 * <pre>{@code
 * @Component
 * public class MyProblemFieldContributor implements ProblemFieldContributor {
 *   @Override
 *   public void contribute(Map<String, Object> fields, Throwable exception) {
 *     fields.put("service", "patra-ingest");
 *     fields.put("timestamp", Instant.now().toString());
 *   }
 * }
 * }</pre>
 *
 * @author Patra Team
 * @since 2.0
 */
public interface ProblemFieldContributor {

  /**
   * 向 {@code ProblemDetail} 响应添加自定义扩展字段。
   *
   * @param fields 用于扩展属性的可变映射(永不为 {@code null})
   * @param exception 正在处理的异常(永不为 {@code null})
   */
  void contribute(Map<String, Object> fields, Throwable exception);
}
