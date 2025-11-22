package com.patra.registry.config;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.exception.RegistryQuotaExceeded;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/// Registry 异常映射贡献者,提供领域异常到错误码的精细化映射。
///
/// 为 Registry 领域异常和数据层异常提供显式映射规则。从 boot 模块迁移到 adapter,避免 boot 直接依赖 domain/api。
///
/// 映射规则:
///
/// - {@link DomainValidationException} → `BAD_REQUEST`
///   - {@link RegistryQuotaExceeded} → `CONFLICT`
///   - {@link DuplicateKeyException} → `CONFLICT`
///   - {@link DataIntegrityViolationException} → `UNPROCESSABLE`
///   - {@link OptimisticLockingFailureException} → `CONFLICT`
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  public RegistryErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /// 将异常映射为错误码。
  ///
  /// @param exception 待映射的异常
  /// @return 如果存在映射则返回错误码,否则返回空
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    String exceptionType = exception.getClass().getSimpleName();
    log.debug("Attempting to map Registry exception [{}] to HTTP error code", exceptionType);

    // Domain validation exceptions
    if (exception instanceof DomainValidationException) {
      return Optional.of(http.BAD_REQUEST());
    }

    // Registry general exceptions
    if (exception instanceof RegistryQuotaExceeded) {
      return Optional.of(http.CONFLICT());
    }

    // Data layer exceptions
    if (exception instanceof DuplicateKeyException) {
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof DataIntegrityViolationException) {
      return Optional.of(http.UNPROCESSABLE());
    }

    if (exception instanceof OptimisticLockingFailureException) {
      return Optional.of(http.CONFLICT());
    }

    log.debug(
        "No mapping found for Registry exception [{}], delegating to default handler",
        exceptionType);
    return Optional.empty();
  }
}
