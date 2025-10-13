package com.patra.starter.mybatis.error.contributor;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataLayerErrorMappingContributorTest {

  private DataLayerErrorMappingContributor contributor;

  @BeforeEach
  void setUp() {
    contributor = new DataLayerErrorMappingContributor(HttpStdErrors.of("ING"));
  }

  @Test
  void mapException_shouldMapMybatisPlusExceptionToInternalError() {
    ErrorCodeLike mapped = contributor.mapException(new MybatisPlusException("fail")).orElseThrow();
    assertThat(mapped.code()).isEqualTo("ING-0500");
    assertThat(mapped.httpStatus()).isEqualTo(500);
  }

  @Test
  void mapException_shouldMapIntegrityViolationToConflict() {
    SQLIntegrityConstraintViolationException ex =
        new SQLIntegrityConstraintViolationException("dup", "23000", 1062);
    ErrorCodeLike mapped = contributor.mapException(ex).orElseThrow();
    assertThat(mapped.code()).isEqualTo("ING-0409");
    assertThat(mapped.httpStatus()).isEqualTo(409);
  }

  @Test
  void mapException_shouldMapSQLExceptionByErrorCode() {
    SQLException duplicate = new SQLException("dup", "HY000", 1062);
    SQLException fk = new SQLException("fk", "HY000", 1452);
    SQLException unavailable = new SQLException("conn", "08000", 0);
    SQLException generic = new SQLException("other", "HY001", 9999);

    assertThat(contributor.mapException(duplicate)).map(ErrorCodeLike::code).hasValue("ING-0409");
    assertThat(contributor.mapException(fk)).map(ErrorCodeLike::code).hasValue("ING-0409");
    assertThat(contributor.mapException(unavailable)).map(ErrorCodeLike::code).hasValue("ING-0503");
    assertThat(contributor.mapException(generic)).map(ErrorCodeLike::code).hasValue("ING-0503");
  }

  @Test
  void mapException_shouldReturnEmptyForUnsupported() {
    assertThat(contributor.mapException(new IllegalStateException("none"))).isEmpty();
  }
}
