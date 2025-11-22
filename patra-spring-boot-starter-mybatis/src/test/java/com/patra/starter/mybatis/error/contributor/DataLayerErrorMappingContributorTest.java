package com.patra.starter.mybatis.error.contributor;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// {@link DataLayerErrorMappingContributor} 的单元测试。
/// 
/// 测试策略：纯单元测试，验证异常到错误码的映射逻辑。
/// 
/// 关键测试点：
/// 
/// - MyBatis-Plus 异常 → INTERNAL_ERROR (500)
///   - SQL 完整性约束异常 → CONFLICT (409)
///   - MySQL 重复键异常(1062) → CONFLICT (409)
///   - MySQL 外键约束异常(1451/1452) → CONFLICT (409)
///   - SQL 连接异常(SQLState: 08xxx) → UNAVAILABLE (503)
///   - SQL 超时异常(SQLState: HYxxx) → UNAVAILABLE (503)
///   - 未知 SQL 异常 → INTERNAL_ERROR (500)
///   - 非数据库异常 → Optional.empty()
/// 
@DisplayName("DataLayerErrorMappingContributor 单元测试")
class DataLayerErrorMappingContributorTest {

  private DataLayerErrorMappingContributor contributor;
  private HttpStdErrors.Group http;

  @BeforeEach
  void setUp() {
    http = HttpStdErrors.of("TEST");
    contributor = new DataLayerErrorMappingContributor(http);
  }

  @Test
  @DisplayName("MyBatisPlusException 应映射为 INTERNAL_ERROR")
  void mapException_mybatisPlusException_shouldReturnInternalError() {
    // Arrange
    MybatisPlusException exception = new MybatisPlusException("MyBatis-Plus 配置错误");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(500);
    assertThat(result.get().code()).isEqualTo("TEST-0500");
  }

  @Test
  @DisplayName("SQLIntegrityConstraintViolationException 应映射为 CONFLICT")
  void mapException_sqlIntegrityConstraintViolation_shouldReturnConflict() {
    // Arrange
    SQLIntegrityConstraintViolationException exception =
        new SQLIntegrityConstraintViolationException("Duplicate entry", "23000");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(409);
    assertThat(result.get().code()).isEqualTo("TEST-0409");
  }

  @Test
  @DisplayName("MySQL 重复键错误(1062) 应映射为 CONFLICT")
  void mapException_mysqlDuplicateKeyError_shouldReturnConflict() {
    // Arrange
    SQLException exception =
        new SQLException("Duplicate entry 'test' for key 'PRIMARY'", "23000", 1062);

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(409);
    assertThat(result.get().code()).isEqualTo("TEST-0409");
  }

  @Test
  @DisplayName("MySQL 外键约束错误(1451) 应映射为 CONFLICT")
  void mapException_mysqlForeignKeyError1451_shouldReturnConflict() {
    // Arrange
    SQLException exception =
        new SQLException("Cannot delete or update a parent row", "23000", 1451);

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(409);
    assertThat(result.get().code()).isEqualTo("TEST-0409");
  }

  @Test
  @DisplayName("MySQL 外键约束错误(1452) 应映射为 CONFLICT")
  void mapException_mysqlForeignKeyError1452_shouldReturnConflict() {
    // Arrange
    SQLException exception = new SQLException("Cannot add or update a child row", "23000", 1452);

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(409);
    assertThat(result.get().code()).isEqualTo("TEST-0409");
  }

  @Test
  @DisplayName("SQL 连接异常(SQLState: 08xxx) 应映射为 UNAVAILABLE")
  void mapException_sqlConnectionError_shouldReturnUnavailable() {
    // Arrange
    SQLException exception = new SQLException("Connection refused", "08001");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(503);
    assertThat(result.get().code()).isEqualTo("TEST-0503");
  }

  @Test
  @DisplayName("SQL 超时异常(SQLState: HYxxx) 应映射为 UNAVAILABLE")
  void mapException_sqlTimeoutError_shouldReturnUnavailable() {
    // Arrange
    SQLException exception = new SQLException("Timeout expired", "HYT00");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(503);
    assertThat(result.get().code()).isEqualTo("TEST-0503");
  }

  @Test
  @DisplayName("未知 SQL 异常应映射为 INTERNAL_ERROR")
  void mapException_unknownSqlException_shouldReturnInternalError() {
    // Arrange
    SQLException exception = new SQLException("Unknown database error", "99999", 9999);

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(500);
    assertThat(result.get().code()).isEqualTo("TEST-0500");
  }

  @Test
  @DisplayName("null SQLState 的 SQL 异常应映射为 INTERNAL_ERROR")
  void mapException_sqlExceptionWithNullSqlState_shouldReturnInternalError() {
    // Arrange
    SQLException exception = new SQLException("No SQLState provided");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(500);
    assertThat(result.get().code()).isEqualTo("TEST-0500");
  }

  @Test
  @DisplayName("非数据库异常应返回 empty Optional")
  void mapException_nonDatabaseException_shouldReturnEmpty() {
    // Arrange
    RuntimeException exception = new RuntimeException("Non-database error");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("NullPointerException 应返回 empty Optional")
  void mapException_nullPointerException_shouldReturnEmpty() {
    // Arrange
    NullPointerException exception = new NullPointerException("Null value");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("SQL 连接异常变体(08003) 应映射为 UNAVAILABLE")
  void mapException_sqlConnectionVariant_shouldReturnUnavailable() {
    // Arrange
    SQLException exception = new SQLException("Connection does not exist", "08003");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(503);
    assertThat(result.get().code()).isEqualTo("TEST-0503");
  }

  @Test
  @DisplayName("SQL 超时异常变体(HY000) 应映射为 UNAVAILABLE")
  void mapException_sqlTimeoutVariant_shouldReturnUnavailable() {
    // Arrange
    SQLException exception = new SQLException("General error", "HY000");

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(503);
    assertThat(result.get().code()).isEqualTo("TEST-0503");
  }

  @Test
  @DisplayName("MySQL 错误码 0 应返回 INTERNAL_ERROR")
  void mapException_mysqlErrorCodeZero_shouldReturnInternalError() {
    // Arrange
    SQLException exception = new SQLException("Unknown error", "42000", 0);

    // Act
    Optional<ErrorCodeLike> result = contributor.mapException(exception);

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get().httpStatus()).isEqualTo(500);
    assertThat(result.get().code()).isEqualTo("TEST-0500");
  }
}
