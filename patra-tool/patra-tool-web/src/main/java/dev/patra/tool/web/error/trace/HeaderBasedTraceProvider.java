package dev.patra.tool.web.error.trace;

import dev.patra.tool.web.error.spi.TraceProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/// 基于 MDC 的 {@link TraceProvider},从配置的头部名称中提取追踪标识符。
@Slf4j
public class HeaderBasedTraceProvider implements TraceProvider {

  /// 追踪配置属性。
  private final List<String> headerNames;

  /// 构造基于 Header 的追踪提供者。
  ///
  /// @param headerNames 候选的 MDC 键，按顺序取第一个非空的

  public HeaderBasedTraceProvider(List<String> headerNames) {
    // 允许列表里有 null（跳过），所以不用 List.copyOf
    this.headerNames = Collections.unmodifiableList(new ArrayList<>(headerNames));
  }

  /// 从 MDC 中提取当前请求的追踪 ID。
  ///
  /// 按配置的 Header 名称列表顺序查找,返回第一个非空的追踪 ID。
  ///
  /// @return 追踪 ID,如果未找到则返回空
  @Override
  public Optional<String> getCurrentTraceId() {
    for (String headerName : headerNames) {
      String traceId = MDC.get(headerName);
      if (traceId != null && !traceId.trim().isEmpty()) {
        log.debug("从 MDC 键 '{}' 找到追踪 ID '{}'", headerName, traceId);
        return Optional.of(traceId.trim());
      }
    }

    log.debug("在 MDC 中未找到任何已配置头部名称的追踪 ID：{}", headerNames);
    return Optional.empty();
  }
}
