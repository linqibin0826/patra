package com.patra.starter.jpa.error.contributor;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import jakarta.persistence.QueryTimeoutException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.JDBCConnectionException;
import org.hibernate.exception.LockAcquisitionException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Component;

/// 将 JPA/Hibernate 数据访问层异常转换为标准化平台错误码的 {@link ErrorMappingContributor}。
///
/// **处理的异常类型**：
///
/// - **JPA 标准异常**：EntityNotFoundException, EntityExistsException, OptimisticLockException 等
/// - **Hibernate 特有异常**：ConstraintViolationException, StaleObjectStateException 等
/// - **Spring Data 异常**：DataIntegrityViolationException, OptimisticLockingFailureException 等
/// - **JDBC 异常**：SQLException 及其子类
///
/// **错误映射规则**：
///
/// | 异常类型 | HTTP 状态 | 说明 |
/// |---------|----------|------|
/// | EntityNotFoundException | 404 | 实体不存在 |
/// | EntityExistsException | 409 | 实体已存在（重复键） |
/// | OptimisticLockException | 409 | 乐观锁冲突 |
/// | ConstraintViolationException | 409 | 约束违反 |
/// | JDBCConnectionException | 503 | 数据库连接问题 |
/// | QueryTimeoutException | 503 | 查询超时 |
/// | 其他 PersistenceException | 500 | 内部错误 |
///
/// **优先级**: {@link Ordered#HIGHEST_PRECEDENCE} - 数据库异常非常常见，应优先处理以提升性能。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JpaErrorMappingContributor implements ErrorMappingContributor {

  private final HttpStdErrors.Group http;

  /// 构造 JPA 错误映射贡献器。
  ///
  /// @param http 标准 HTTP 错误定义组
  public JpaErrorMappingContributor(HttpStdErrors.Group http) {
    this.http = http;
  }

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    // 1. Spring Data 异常（最外层包装）
    Optional<ErrorCodeLike> springDataError = mapSpringDataExceptions(exception);
    if (springDataError.isPresent()) {
      return springDataError;
    }

    // 2. JPA 标准异常
    Optional<ErrorCodeLike> jpaError = mapJpaExceptions(exception);
    if (jpaError.isPresent()) {
      return jpaError;
    }

    // 3. Hibernate 特有异常
    Optional<ErrorCodeLike> hibernateError = mapHibernateExceptions(exception);
    if (hibernateError.isPresent()) {
      return hibernateError;
    }

    // 4. SQL 异常
    Optional<ErrorCodeLike> sqlError = mapSqlExceptions(exception);
    if (sqlError.isPresent()) {
      return sqlError;
    }

    return Optional.empty();
  }

  /// 映射 Spring Data 异常。
  private Optional<ErrorCodeLike> mapSpringDataExceptions(Throwable exception) {
    if (exception instanceof DataIntegrityViolationException ex) {
      log.debug("将数据完整性违反异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof OptimisticLockingFailureException ex) {
      log.debug("将乐观锁失败异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof PessimisticLockingFailureException ex) {
      log.debug("将悲观锁失败异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof JpaSystemException ex) {
      log.error("将 JPA 系统异常映射为内部服务器错误", ex);
      return Optional.of(http.INTERNAL_ERROR());
    }

    return Optional.empty();
  }

  /// 映射 JPA 标准异常。
  private Optional<ErrorCodeLike> mapJpaExceptions(Throwable exception) {
    if (exception instanceof EntityNotFoundException ex) {
      log.debug("将实体不存在异常映射为未找到错误", ex);
      return Optional.of(http.NOT_FOUND());
    }

    if (exception instanceof EntityExistsException ex) {
      log.debug("将实体已存在异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof OptimisticLockException ex) {
      log.debug("将乐观锁异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof PessimisticLockException ex) {
      log.debug("将悲观锁异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof LockTimeoutException ex) {
      log.warn("将锁超时异常映射为服务不可用", ex);
      return Optional.of(http.UNAVAILABLE());
    }

    if (exception instanceof QueryTimeoutException ex) {
      log.warn("将查询超时异常映射为服务不可用", ex);
      return Optional.of(http.UNAVAILABLE());
    }

    if (exception instanceof PersistenceException ex) {
      log.error("将通用持久化异常映射为内部服务器错误", ex);
      return Optional.of(http.INTERNAL_ERROR());
    }

    return Optional.empty();
  }

  /// 映射 Hibernate 特有异常。
  private Optional<ErrorCodeLike> mapHibernateExceptions(Throwable exception) {
    if (exception instanceof ConstraintViolationException ex) {
      log.debug("将 Hibernate 约束违反异常（约束名: {}）映射为冲突错误", ex.getConstraintName(), ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof StaleObjectStateException ex) {
      log.debug("将 Hibernate 陈旧对象状态异常（实体: {}）映射为冲突错误", ex.getEntityName(), ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof JDBCConnectionException ex) {
      log.warn("将 JDBC 连接异常映射为服务不可用", ex);
      return Optional.of(http.UNAVAILABLE());
    }

    if (exception instanceof LockAcquisitionException ex) {
      log.warn("将锁获取异常映射为冲突错误", ex);
      return Optional.of(http.CONFLICT());
    }

    return Optional.empty();
  }

  /// 映射 SQL 异常。
  private Optional<ErrorCodeLike> mapSqlExceptions(Throwable exception) {
    if (exception instanceof SQLIntegrityConstraintViolationException ex) {
      log.debug("将 SQL 完整性约束违反（SQLState: {}）映射为冲突错误", ex.getSQLState(), ex);
      return Optional.of(http.CONFLICT());
    }

    if (exception instanceof SQLException sqlEx) {
      // MySQL 特定错误码映射
      Optional<ErrorCodeLike> mysqlResult =
          switch (sqlEx.getErrorCode()) {
            case 1062 -> { // ER_DUP_ENTRY: 唯一键的重复条目
              log.debug("将 MySQL 重复条目错误 ({}) 映射为冲突", sqlEx.getErrorCode(), sqlEx);
              yield Optional.of(http.CONFLICT());
            }
            case 1451, 1452 -> { // ER_ROW_IS_REFERENCED_2, ER_NO_REFERENCED_ROW_2: 外键约束
              log.debug("将 MySQL 外键约束错误 ({}) 映射为冲突", sqlEx.getErrorCode(), sqlEx);
              yield Optional.of(http.CONFLICT());
            }
            default -> Optional.empty();
          };
      if (mysqlResult.isPresent()) {
        return mysqlResult;
      }

      // SQLState 映射
      String sqlState = sqlEx.getSQLState();
      if (sqlState != null && (sqlState.startsWith("08") || sqlState.startsWith("HY"))) {
        log.warn("将 SQL 连接/超时错误（SQLState: {}）映射为服务不可用", sqlState, sqlEx);
        return Optional.of(http.UNAVAILABLE());
      }

      log.error(
          "将未处理的 SQL 异常（SQLState: {}, ErrorCode: {}）映射为内部服务器错误",
          sqlEx.getSQLState(),
          sqlEx.getErrorCode(),
          sqlEx);
      return Optional.of(http.INTERNAL_ERROR());
    }

    return Optional.empty();
  }
}
