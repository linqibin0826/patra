package com.patra.starter.redisson.exception;

import com.patra.common.error.codes.ErrorCodeLike;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LockErrorCode} 单元测试
 *
 * @author Patra Team
 * @since 1.0.0
 */
@DisplayName("LockErrorCode 错误码枚举测试")
class LockErrorCodeTest {

    @Test
    @DisplayName("应该实现 ErrorCodeLike 接口")
    void shouldImplementErrorCodeLike() {
        assertThat(LockErrorCode.ACQUISITION_FAILED).isInstanceOf(ErrorCodeLike.class);
    }

    @Test
    @DisplayName("ACQUISITION_FAILED 应该返回正确的错误码和 HTTP 状态码")
    void acquisitionFailed_ShouldReturnCorrectCodeAndStatus() {
        LockErrorCode errorCode = LockErrorCode.ACQUISITION_FAILED;

        assertThat(errorCode.code()).isEqualTo("LOCK_001");
        assertThat(errorCode.httpStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("TIMEOUT 应该返回正确的错误码和 HTTP 状态码")
    void timeout_ShouldReturnCorrectCodeAndStatus() {
        LockErrorCode errorCode = LockErrorCode.TIMEOUT;

        assertThat(errorCode.code()).isEqualTo("LOCK_002");
        assertThat(errorCode.httpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("INFRASTRUCTURE_ERROR 应该返回正确的错误码和 HTTP 状态码")
    void infrastructureError_ShouldReturnCorrectCodeAndStatus() {
        LockErrorCode errorCode = LockErrorCode.INFRASTRUCTURE_ERROR;

        assertThat(errorCode.code()).isEqualTo("LOCK_003");
        assertThat(errorCode.httpStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("EXPRESSION_ERROR 应该返回正确的错误码和 HTTP 状态码")
    void expressionError_ShouldReturnCorrectCodeAndStatus() {
        LockErrorCode errorCode = LockErrorCode.EXPRESSION_ERROR;

        assertThat(errorCode.code()).isEqualTo("LOCK_004");
        assertThat(errorCode.httpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("所有错误码应该唯一")
    void allErrorCodes_ShouldBeUnique() {
        String[] codes = {
            LockErrorCode.ACQUISITION_FAILED.code(),
            LockErrorCode.TIMEOUT.code(),
            LockErrorCode.INFRASTRUCTURE_ERROR.code(),
            LockErrorCode.EXPRESSION_ERROR.code()
        };

        assertThat(codes).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("所有 HTTP 状态码应该在有效范围内（100-599）")
    void allHttpStatusCodes_ShouldBeValid() {
        for (LockErrorCode errorCode : LockErrorCode.values()) {
            assertThat(errorCode.httpStatus())
                .isGreaterThanOrEqualTo(100)
                .isLessThan(600);
        }
    }
}
