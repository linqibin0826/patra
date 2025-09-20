package com.patra.starter.web.error.handler;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.web.error.builder.ProblemDetailBuilder;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for GlobalRestExceptionHandler.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class GlobalRestExceptionHandlerTest {
    
    @Mock
    private ErrorResolutionService errorResolutionService;
    
    @Mock
    private ProblemDetailBuilder problemDetailBuilder;
    
    @Mock
    private ValidationErrorsFormatter validationErrorsFormatter;
    
    private GlobalRestExceptionHandler exceptionHandler;
    
    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalRestExceptionHandler(
            errorResolutionService, 
            problemDetailBuilder, 
            validationErrorsFormatter
        );
    }
    
    /**
     * Test handling of ApplicationException.
     */
    @Test
    void shouldHandleApplicationException() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        ApplicationException exception = new ApplicationException(errorCode, "Test application exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/application-exception");
        
        ProblemDetail problemDetail = ProblemDetail.forStatus(422);
        problemDetail.setTitle("TEST-0001");
        problemDetail.setDetail("Test application exception");
        problemDetail.setProperty(com.patra.common.error.problem.ErrorKeys.PATH, "/test/application-exception");
        
        when(errorResolutionService.resolve(eq(exception))).thenReturn(resolution);
        when(problemDetailBuilder.build(eq(resolution), eq(exception), eq(request))).thenReturn(problemDetail);
        
        // When
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleException(exception, request);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isEqualTo(problemDetail);
    }
    
    /**
     * Test handling of generic RuntimeException.
     */
    @Test
    void shouldHandleGenericException() {
        // Given
        TestErrorCode errorCode = TestErrorCode.GENERIC_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 500);
        RuntimeException exception = new RuntimeException("Generic runtime exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/runtime-exception");
        
        ProblemDetail problemDetail = ProblemDetail.forStatus(500);
        problemDetail.setTitle("TEST-0500");
        problemDetail.setDetail("Generic runtime exception");
        problemDetail.setProperty(com.patra.common.error.problem.ErrorKeys.PATH, "/test/runtime-exception");
        
        when(errorResolutionService.resolve(eq(exception))).thenReturn(resolution);
        when(problemDetailBuilder.build(eq(resolution), eq(exception), eq(request))).thenReturn(problemDetail);
        
        // When
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleException(exception, request);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(problemDetail);
    }
    
    /**
     * Test error codes for testing purposes.
     */
    enum TestErrorCode implements ErrorCodeLike {
        TEST_ERROR("TEST-0001"),
        GENERIC_ERROR("TEST-0500");
        
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