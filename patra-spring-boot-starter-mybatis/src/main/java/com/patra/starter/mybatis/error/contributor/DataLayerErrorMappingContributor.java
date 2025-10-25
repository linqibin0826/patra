package com.patra.starter.mybatis.error.contributor;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * An {@link ErrorMappingContributor} that translates exceptions from the data access layer
 * (specifically MyBatis-Plus and underlying JDBC drivers) into standardized platform error codes.
 *
 * <p>This component plays a crucial role in the global error handling strategy by ensuring that
 * low-level database errors are converted into meaningful, consistent HTTP response codes. It
 * handles common issues like data conflicts, constraint violations, and connectivity problems.
 */
@Slf4j
@Component
public class DataLayerErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  public DataLayerErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /**
   * Maps a given {@link Throwable} to a corresponding {@link ErrorCodeLike} if it originates from
   * the data access layer.
   *
   * @param exception The exception to map.
   * @return An {@link Optional} containing the mapped {@link ErrorCodeLike}, or an empty optional
   *     if the exception is not handled by this contributor.
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
      log.debug(
          "Mapping SQL integrity constraint violation (SQLState: {}) to a conflict error.",
          sqlEx.getSQLState(),
          sqlEx);
      return Optional.of(http.CONFLICT());
    }

    // Handle other common SQL exceptions by examining their SQLState and vendor-specific error
    // codes.
    if (exception instanceof SQLException sqlEx) {
      return mapCommonSqlExceptions(sqlEx);
    }

    return Optional.empty();
  }

  /**
   * Analyzes a {@link SQLException} to determine the most appropriate error code.
   *
   * @param sqlEx the SQL exception to analyze
   * @return an optional containing the mapped error code
   */
  private Optional<ErrorCodeLike> mapCommonSqlExceptions(SQLException sqlEx) {
    Optional<ErrorCodeLike> mysqlError = mapMysqlSpecificErrors(sqlEx);
    if (mysqlError.isPresent()) {
      return mysqlError;
    }

    Optional<ErrorCodeLike> sqlStateError = mapSqlStateErrors(sqlEx);
    if (sqlStateError.isPresent()) {
      return sqlStateError;
    }

    return mapUnhandledSqlException(sqlEx);
  }

  /**
   * Maps MySQL-specific error codes to appropriate error responses.
   *
   * @param sqlEx the SQL exception containing MySQL error code
   * @return optional error code if MySQL error is recognized
   */
  private Optional<ErrorCodeLike> mapMysqlSpecificErrors(SQLException sqlEx) {
    int errorCode = sqlEx.getErrorCode();

    switch (errorCode) {
      case 1062: // ER_DUP_ENTRY: Duplicate entry for a unique key
        log.debug("Mapping MySQL duplicate entry error ({}) to conflict", errorCode, sqlEx);
        return Optional.of(http.CONFLICT());
      case 1451: // ER_ROW_IS_REFERENCED_2: Cannot delete/update parent row
      case 1452: // ER_NO_REFERENCED_ROW_2: Cannot add/update child row
        log.debug("Mapping MySQL foreign key constraint error ({}) to conflict", errorCode, sqlEx);
        return Optional.of(http.CONFLICT());
      default:
        return Optional.empty();
    }
  }

  /**
   * Maps SQLState codes to appropriate error responses.
   *
   * @param sqlEx the SQL exception containing SQLState code
   * @return optional error code if SQLState indicates connection or timeout issue
   */
  private Optional<ErrorCodeLike> mapSqlStateErrors(SQLException sqlEx) {
    String sqlState = sqlEx.getSQLState();
    if (sqlState == null) {
      return Optional.empty();
    }

    // SQLState '08' = connection exception, 'HY' = timeout
    if (sqlState.startsWith("08") || sqlState.startsWith("HY")) {
      log.warn(
          "Mapping SQL connection/timeout error (SQLState: {}) to service unavailable",
          sqlState,
          sqlEx);
      return Optional.of(http.UNAVAILABLE());
    }

    return Optional.empty();
  }

  /**
   * Maps unhandled SQL exceptions to internal server error.
   *
   * @param sqlEx the unhandled SQL exception
   * @return error code for internal server error
   */
  private Optional<ErrorCodeLike> mapUnhandledSqlException(SQLException sqlEx) {
    log.error(
        "Mapping unhandled SQL exception (SQLState: {}, ErrorCode: {}) to internal server error",
        sqlEx.getSQLState(),
        sqlEx.getErrorCode(),
        sqlEx);
    return Optional.of(http.INTERNAL_ERROR());
  }
}
