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

/**
 * Registry-specific error mapping contributor for fine-grained exception to error code mapping.
 *
 * <p>Provides explicit mappings for Registry domain exceptions and data layer exceptions. Migrated
 * from boot module to adapter to avoid boot directly depending on domain/api.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  public RegistryErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /**
   * Maps an exception to an error code.
   *
   * @param exception the exception to map
   * @return optional containing the mapped error code if a mapping exists
   */
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    log.debug(
        "map exception start exception={} mapping=TRY",
        exception.getClass().getSimpleName());

    // Domain validation exceptions
    if (exception instanceof DomainValidationException) {
      // General domain parameter validation -> 400
      return Optional.of(http.BAD_REQUEST());
    }

    // Registry general exceptions
    if (exception instanceof RegistryQuotaExceeded) {
      return Optional.of(http.CONFLICT()); // 409
    }

    // Data layer exceptions
    if (exception instanceof DuplicateKeyException) {
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof DataIntegrityViolationException) {
      return Optional.of(http.UNPROCESSABLE()); // 422
    }

    if (exception instanceof OptimisticLockingFailureException) {
      return Optional.of(http.CONFLICT());
    }

    log.debug(
        "map exception miss exception={}",
        exception.getClass().getSimpleName());
    return Optional.empty();
  }
}
