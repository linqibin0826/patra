package com.patra.starter.mybatis.error.contributor;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

/**
 * An {@link ErrorMappingContributor} that translates exceptions from the data access layer
 * (specifically MyBatis-Plus and underlying JDBC drivers) into standardized platform error codes.
 * <p>
 * This component plays a crucial role in the global error handling strategy by ensuring that
 * low-level database errors are converted into meaningful, consistent HTTP response codes.
 * It handles common issues like data conflicts, constraint violations, and connectivity problems.
 * </p>
 */
@Slf4j
@Component
public class DataLayerErrorMappingContributor implements ErrorMappingContributor {

    private final HttpStdErrors.Group http;

    public DataLayerErrorMappingContributor(HttpStdErrors.Group http) {
        this.http = http;
    }

    /**
     * Maps a given {@link Throwable} to a corresponding {@link ErrorCodeLike} if it originates
     * from the data access layer.
     *
     * @param exception The exception to map.
     * @return An {@link Optional} containing the mapped {@link ErrorCodeLike}, or an empty optional
     * if the exception is not handled by this contributor.
     */
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // Generic MyBatis-Plus exceptions are treated as internal server errors,
        // as they often indicate configuration or mapping problems.
        if (exception instanceof MybatisPlusException) {
            log.debug("Mapping generic MyBatis-Plus exception to an internal server error.", exception);
            return Optional.of(http.INTERNAL_ERROR());
        }

        // SQLIntegrityConstraintViolationException indicates a conflict, such as a duplicate key.
        if (exception instanceof SQLIntegrityConstraintViolationException sqlEx) {
            log.debug("Mapping SQL integrity constraint violation (SQLState: {}) to a conflict error.",
                    sqlEx.getSQLState(), sqlEx);
            return Optional.of(http.CONFLICT());
        }

        // Handle other common SQL exceptions by examining their SQLState and vendor-specific error codes.
        if (exception instanceof SQLException sqlEx) {
            return mapCommonSqlExceptions(sqlEx);
        }

        return Optional.empty();
    }

    /**
     * Analyzes a {@link SQLException} to determine the most appropriate error code.
     *
     * @param sqlEx The SQL exception.
     * @return An optional containing the mapped error code.
     */
    private Optional<ErrorCodeLike> mapCommonSqlExceptions(SQLException sqlEx) {
        int errorCode = sqlEx.getErrorCode();
        String sqlState = sqlEx.getSQLState();

        // Specific MySQL error codes are mapped to HTTP 409 Conflict.
        switch (errorCode) {
            case 1062: // ER_DUP_ENTRY: Duplicate entry for a unique key.
                log.debug("Mapping MySQL duplicate entry error ({}) to a conflict.", errorCode, sqlEx);
                return Optional.of(http.CONFLICT());
            case 1451: // ER_ROW_IS_REFERENCED_2: Cannot delete or update a parent row (foreign key constraint).
            case 1452: // ER_NO_REFERENCED_ROW_2: Cannot add or update a child row (foreign key constraint fails).
                log.debug("Mapping MySQL foreign key constraint error ({}) to a conflict.", errorCode, sqlEx);
                return Optional.of(http.CONFLICT());
        }

        // SQLState prefixes '08' (connection exception) and 'HY' (timeout) indicate service unavailability.
        if (sqlState != null && (sqlState.startsWith("08") || sqlState.startsWith("HY"))) {
            log.warn("Mapping SQL connection/timeout error (SQLState: {}) to service unavailable.", sqlState, sqlEx);
            return Optional.of(http.UNAVAILABLE());
        }

        // As a fallback, other SQL exceptions are treated as internal server errors.
        log.error("Mapping unhandled SQL exception (SQLState: {}, ErrorCode: {}) to an internal server error.",
                sqlState, errorCode, sqlEx);
        return Optional.of(http.INTERNAL_ERROR());
    }
}