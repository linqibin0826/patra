package com.patra.ingest.config;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.ingest.api.error.IngestErrorCode;
import com.patra.ingest.domain.exception.IngestConfigurationException;
import com.patra.ingest.domain.exception.IngestScheduleParameterException;
import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.exception.OutboxRelayExecutionException;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.exception.TaskCheckpointException;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.util.RemoteErrorHelper;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Contributes ingest-specific exception to error-code mappings.
 *
 * <p>Registers the domain exceptions emitted by the ingest service so the platform error parsing
 * engine can convert them into consistent {@link IngestErrorCode} responses.
 */
@Slf4j
@Component
public class IngestErrorMappingContributor implements ErrorMappingContributor {

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    Optional<ErrorCodeLike> errorCode =
        tryMapConfigurationException(exception)
            .or(() -> tryMapScheduleException(exception))
            .or(() -> tryMapCheckpointException(exception))
            .or(() -> tryMapPlanException(exception))
            .or(() -> tryMapOutboxException(exception));

    if (errorCode.isEmpty()) {
      log.debug(
          "No error code mapping found for exception type: {}", exception.getClass().getName());
    } else {
      log.debug(
          "Mapped exception {} to error code: {}",
          exception.getClass().getSimpleName(),
          errorCode.get().code());
    }

    return errorCode;
  }

  /**
   * Maps configuration-related exceptions to error codes.
   *
   * @param exception the throwable to attempt mapping
   * @return optional error code if exception type matches
   */
  private Optional<ErrorCodeLike> tryMapConfigurationException(Throwable exception) {
    if (exception instanceof IngestConfigurationException configurationException) {
      return Optional.of(resolveConfigurationError(configurationException));
    }
    return Optional.empty();
  }

  /**
   * Maps schedule parameter exceptions to error codes.
   *
   * @param exception the throwable to attempt mapping
   * @return optional error code if exception type matches
   */
  private Optional<ErrorCodeLike> tryMapScheduleException(Throwable exception) {
    if (exception instanceof IngestScheduleParameterException) {
      return Optional.of(IngestErrorCode.ING_1401);
    }
    if (exception instanceof OutboxRelayExecutionException) {
      return Optional.of(IngestErrorCode.ING_1402);
    }
    if (exception instanceof PlanValidationException) {
      return Optional.of(IngestErrorCode.ING_1403);
    }
    return Optional.empty();
  }

  /**
   * Maps checkpoint-related exceptions to error codes.
   *
   * @param exception the throwable to attempt mapping
   * @return optional error code if exception type matches
   */
  private Optional<ErrorCodeLike> tryMapCheckpointException(Throwable exception) {
    if (exception instanceof TaskCheckpointException checkpointException) {
      if (checkpointException.getType() == TaskCheckpointException.Type.PARSE) {
        return Optional.of(IngestErrorCode.ING_1501);
      }
      if (checkpointException.getType() == TaskCheckpointException.Type.SERIALIZE) {
        return Optional.of(IngestErrorCode.ING_1502);
      }
    }
    return Optional.empty();
  }

  /**
   * Maps plan-related exceptions to error codes.
   *
   * @param exception the throwable to attempt mapping
   * @return optional error code if exception type matches
   */
  private Optional<ErrorCodeLike> tryMapPlanException(Throwable exception) {
    if (exception instanceof PlanAssemblyException) {
      return Optional.of(IngestErrorCode.ING_1601);
    }
    if (exception instanceof PlanPersistenceException persistenceException) {
      return Optional.of(resolvePlanPersistence(persistenceException));
    }
    return Optional.empty();
  }

  /**
   * Maps outbox-related exceptions to error codes.
   *
   * @param exception the throwable to attempt mapping
   * @return optional error code if exception type matches
   */
  private Optional<ErrorCodeLike> tryMapOutboxException(Throwable exception) {
    if (exception instanceof OutboxPersistenceException persistenceException) {
      return Optional.of(resolveOutboxPersistence(persistenceException));
    }
    return Optional.empty();
  }

  /**
   * Resolves configuration error based on remote call exception details.
   *
   * @param exception the configuration exception to analyze
   * @return appropriate error code based on remote failure type
   */
  private ErrorCodeLike resolveConfigurationError(IngestConfigurationException exception) {
    Throwable cause = exception.getCause();
    if (cause instanceof RemoteCallException remote) {
      if (RemoteErrorHelper.isNotFound(remote)) {
        return IngestErrorCode.ING_1201;
      }
      if (RemoteErrorHelper.isServerError(remote) || RemoteErrorHelper.isRetryable(remote)) {
        return IngestErrorCode.ING_1203;
      }
      if (RemoteErrorHelper.isClientError(remote)) {
        return IngestErrorCode.ING_1202;
      }
    }
    return IngestErrorCode.ING_1202;
  }

  /**
   * Resolves outbox persistence error based on operation stage.
   *
   * @param exception the outbox persistence exception with stage information
   * @return appropriate error code based on the failed persistence stage
   */
  private ErrorCodeLike resolveOutboxPersistence(OutboxPersistenceException exception) {
    return switch (exception.getStage()) {
      case MARK_PUBLISHED -> IngestErrorCode.ING_1302;
      case MARK_RETRY -> IngestErrorCode.ING_1301;
      case MARK_DEAD -> IngestErrorCode.ING_1303;
    };
  }

  /**
   * Resolves plan persistence error based on operation stage.
   *
   * @param exception the plan persistence exception with stage information
   * @return appropriate error code based on the failed persistence stage
   */
  private ErrorCodeLike resolvePlanPersistence(PlanPersistenceException exception) {
    return switch (exception.getStage()) {
      case SCHEDULE_INSTANCE, PLAN, PLAN_SLICE, TASK, TASK_RETRY -> IngestErrorCode.ING_1503;
    };
  }
}
