package com.patra.common.error;

import com.patra.common.error.codes.ErrorCodeLike;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ApplicationException class.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class ApplicationExceptionTest {
    
    /**
     * Test application exception with error code and message.
     */
    @Test
    void shouldCreateApplicationExceptionWithErrorCodeAndMessage() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        String message = "Test application exception message";
        
        // When
        ApplicationException exception = new ApplicationException(errorCode, message);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test application exception with error code, message and cause.
     */
    @Test
    void shouldCreateApplicationExceptionWithErrorCodeMessageAndCause() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        String message = "Test application exception message";
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        ApplicationException exception = new ApplicationException(errorCode, message, cause);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test application exception with null error code should throw IllegalArgumentException.
     */
    @Test
    void shouldThrowIllegalArgumentExceptionForNullErrorCode() {
        // Given
        String message = "Test message";
        
        // When & Then
        assertThatThrownBy(() -> new ApplicationException(null, message))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ErrorCode cannot be null");
    }
    
    /**
     * Test application exception with null message is allowed.
     */
    @Test
    void shouldAllowNullMessage() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        
        // When
        ApplicationException exception = new ApplicationException(errorCode, null);
        
        // Then
        assertThat(exception.getErrorCode()).isEqualTo(errorCode);
        assertThat(exception.getMessage()).isNull();
    }
    
    /**
     * Test application exception inheritance hierarchy.
     */
    @Test
    void shouldMaintainInheritanceHierarchy() {
        // Given
        ApplicationException exception = new ApplicationException(TestErrorCode.TEST_ERROR, "test");
        
        // Then
        assertThat(exception).isInstanceOf(ApplicationException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isInstanceOf(Exception.class);
        assertThat(exception).isInstanceOf(Throwable.class);
    }
    
    /**
     * Test error code enum for testing purposes.
     */
    private enum TestErrorCode implements ErrorCodeLike {
        TEST_ERROR("TEST-0001"),
        ANOTHER_ERROR("TEST-0002");
        
        private final String code;
        
        TestErrorCode(String code) {
            this.code = code;
        }
        
        @Override
        public String code() {
            return code;
        }
    }
}