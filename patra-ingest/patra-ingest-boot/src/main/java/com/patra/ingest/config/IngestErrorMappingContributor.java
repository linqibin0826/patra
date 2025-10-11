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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Contributes ingest-specific exception to error-code mappings.
 *
 * <p>Registers the domain exceptions emitted by the ingest service so the platform error parsing
 * engine can convert them into consistent {@link IngestErrorCode} responses.</p>
 */
@Slf4j
@Component
public class IngestErrorMappingContributor implements ErrorMappingContributor {

    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        if (exception instanceof IngestConfigurationException configurationException) {
            return Optional.of(resolveConfigurationError(configurationException));
        }

        if (exception instanceof IngestScheduleParameterException) {
            return Optional.of(IngestErrorCode.ING_1401);
        }

        if (exception instanceof OutboxRelayExecutionException) {
            return Optional.of(IngestErrorCode.ING_1402);
        }

        if (exception instanceof TaskCheckpointException checkpointException) {
            if (checkpointException.getType() == TaskCheckpointException.Type.PARSE) {
                return Optional.of(IngestErrorCode.ING_1501);
            }
            if (checkpointException.getType() == TaskCheckpointException.Type.SERIALIZE) {
                return Optional.of(IngestErrorCode.ING_1502);
            }
        }

        if (exception instanceof PlanValidationException) {
            return Optional.of(IngestErrorCode.ING_1403);
        }

        if (exception instanceof PlanAssemblyException) {
            return Optional.of(IngestErrorCode.ING_1601);
        }

        if (exception instanceof PlanPersistenceException persistenceException) {
            return Optional.of(resolvePlanPersistence(persistenceException));
        }

        if (exception instanceof OutboxPersistenceException persistenceException) {
            return Optional.of(resolveOutboxPersistence(persistenceException));
        }

        return Optional.empty();
    }

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

    private ErrorCodeLike resolveOutboxPersistence(OutboxPersistenceException exception) {
        return switch (exception.getStage()) {
            case MARK_PUBLISHED -> IngestErrorCode.ING_1302;
            case MARK_RETRY -> IngestErrorCode.ING_1301;
            case MARK_DEAD -> IngestErrorCode.ING_1303;
        };
    }

    private ErrorCodeLike resolvePlanPersistence(PlanPersistenceException exception) {
        return switch (exception.getStage()) {
            case SCHEDULE_INSTANCE, PLAN, PLAN_SLICE, TASK, TASK_RETRY -> IngestErrorCode.ING_1503;
        };
    }
}
