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
 * 将数据访问层异常（特别是 MyBatis-Plus 和底层 JDBC 驱动程序）转换为标准化平台错误码的 {@link ErrorMappingContributor}。
 *
 * <p>此组件通过确保将低级数据库错误转换为有意义且一致的 HTTP 响应代码，在全局错误处理策略中发挥着至关重要的作用。它处理常见问题，如数据冲突、约束违规和连接问题。
 */
@Slf4j
@Component
public class DataLayerErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  public DataLayerErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  /**
   * 如果异常源自数据访问层，将给定的 {@link Throwable} 映射到相应的 {@link ErrorCodeLike}。
   *
   * @param exception 要映射的异常
   * @return 包含映射的 {@link ErrorCodeLike} 的 {@link Optional}，如果此贡献器未处理该异常，则返回空 Optional
   */
  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    // 通用 MyBatis-Plus 异常被视为内部服务器错误，因为它们通常表示配置或映射问题。
    if (exception instanceof MybatisPlusException) {
      log.debug("将通用 MyBatis-Plus 异常映射为内部服务器错误。", exception);
      return Optional.of(http.INTERNAL_ERROR());
    }

    // SQLIntegrityConstraintViolationException 表示冲突，例如重复键。
    if (exception instanceof SQLIntegrityConstraintViolationException sqlEx) {
      log.debug("将 SQL 完整性约束违规（SQLState: {}）映射为冲突错误。", sqlEx.getSQLState(), sqlEx);
      return Optional.of(http.CONFLICT());
    }

    // 通过检查 SQLState 和特定供应商的错误代码来处理其他常见的 SQL 异常。
    if (exception instanceof SQLException sqlEx) {
      return mapCommonSqlExceptions(sqlEx);
    }

    return Optional.empty();
  }

  /**
   * 分析 {@link SQLException} 以确定最合适的错误代码。
   *
   * @param sqlEx 要分析的 SQL 异常
   * @return 包含映射错误代码的 Optional
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
   * 将 MySQL 特定的错误代码映射到适当的错误响应。
   *
   * @param sqlEx 包含 MySQL 错误代码的 SQL 异常
   * @return 如果识别出 MySQL 错误，则返回 Optional 错误代码
   */
  private Optional<ErrorCodeLike> mapMysqlSpecificErrors(SQLException sqlEx) {
    int errorCode = sqlEx.getErrorCode();

    switch (errorCode) {
      case 1062: // ER_DUP_ENTRY: 唯一键的重复条目
        log.debug("将 MySQL 重复条目错误 ({}) 映射为冲突", errorCode, sqlEx);
        return Optional.of(http.CONFLICT());
      case 1451: // ER_ROW_IS_REFERENCED_2: 无法删除/更新父行
      case 1452: // ER_NO_REFERENCED_ROW_2: 无法添加/更新子行
        log.debug("将 MySQL 外键约束错误 ({}) 映射为冲突", errorCode, sqlEx);
        return Optional.of(http.CONFLICT());
      default:
        return Optional.empty();
    }
  }

  /**
   * 将 SQLState 代码映射到适当的错误响应。
   *
   * @param sqlEx 包含 SQLState 代码的 SQL 异常
   * @return 如果 SQLState 指示连接或超时问题，则返回 Optional 错误代码
   */
  private Optional<ErrorCodeLike> mapSqlStateErrors(SQLException sqlEx) {
    String sqlState = sqlEx.getSQLState();
    if (sqlState == null) {
      return Optional.empty();
    }

    // SQLState '08' = 连接异常, 'HY' = 超时
    if (sqlState.startsWith("08") || sqlState.startsWith("HY")) {
      log.warn("将 SQL 连接/超时错误（SQLState: {}）映射为服务不可用", sqlState, sqlEx);
      return Optional.of(http.UNAVAILABLE());
    }

    return Optional.empty();
  }

  /**
   * 将未处理的 SQL 异常映射为内部服务器错误。
   *
   * @param sqlEx 未处理的 SQL 异常
   * @return 内部服务器错误的错误代码
   */
  private Optional<ErrorCodeLike> mapUnhandledSqlException(SQLException sqlEx) {
    log.error(
        "将未处理的 SQL 异常（SQLState: {}, ErrorCode: {}）映射为内部服务器错误",
        sqlEx.getSQLState(),
        sqlEx.getErrorCode(),
        sqlEx);
    return Optional.of(http.INTERNAL_ERROR());
  }
}
