package dev.patra.analytics.config;

import dev.patra.analytics.domain.collect.exception.BatchTooLargeException;
import dev.patra.analytics.domain.collect.exception.EventPublishUnavailableException;
import dev.patra.analytics.domain.collect.exception.InvalidBatchException;
import dev.patra.tool.core.error.codes.ErrorCodeLike;
import dev.patra.tool.core.error.codes.HttpStdErrors;
import dev.patra.tool.web.error.spi.ErrorMappingContributor;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// analytics 的异常映射：采集批次的三种拒收（采集 spec §4.1）。
///
/// - `InvalidBatchException` → 400（不是合法 JSON、外壳不合格）
/// - `BatchTooLargeException` → 413（请求体超过 1 MiB）
/// - `EventPublishUnavailableException` → 503（Kafka 写不进）
@Component
@Order(50)
public class AnalyticsErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 创建映射。
  ///
  /// @param http HTTP 标准错误码分组
  public AnalyticsErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /// 将异常映射为错误码。
  ///
  /// @param exception 待映射的异常
  /// @return 错误码；不是本服务的异常时为空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    return switch (exception) {
      case InvalidBatchException e -> Optional.of(http.BAD_REQUEST());
      case BatchTooLargeException e -> Optional.of(http.CONTENT_TOO_LARGE());
      case EventPublishUnavailableException e -> Optional.of(http.UNAVAILABLE());
      default -> Optional.empty();
    };
  }
}
