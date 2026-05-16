package dev.linqibin.patra.registry.config;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import dev.linqibin.commons.error.codes.HttpStdErrors;
import dev.linqibin.patra.registry.domain.exception.DomainValidationException;
import dev.linqibin.patra.registry.domain.exception.RegistryQuotaExceeded;
import dev.linqibin.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/// Registry 异常映射贡献者，提供领域异常到错误码的精细化映射。
///
/// 仅映射 Registry 特有的领域异常。数据层异常（如 DataIntegrityViolationException、
/// OptimisticLockingFailureException）由更高优先级的 JpaErrorMappingContributor 统一处理。
///
/// 映射规则：
///
/// - {@link DomainValidationException} → `BAD_REQUEST`
/// - {@link RegistryQuotaExceeded} → `CONFLICT`
///
/// **优先级**: 50（中等优先级）- 业务特定异常，在基础设施异常之后处理。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@Order(50)
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 构造 Registry 异常映射贡献者。
  ///
  /// @param http HTTP 标准错误码分组
  public RegistryErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /// 将异常映射为错误码。
  ///
  /// @param exception 待映射的异常
  /// @return 如果存在映射则返回错误码，否则返回空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    String exceptionType = exception.getClass().getSimpleName();
    log.debug("Attempting to map Registry exception [{}] to HTTP error code", exceptionType);

    if (exception instanceof DomainValidationException) {
      return Optional.of(http.BAD_REQUEST());
    }

    if (exception instanceof RegistryQuotaExceeded) {
      return Optional.of(http.CONFLICT());
    }

    log.debug(
        "No mapping found for Registry exception [{}], delegating to default handler",
        exceptionType);
    return Optional.empty();
  }
}
