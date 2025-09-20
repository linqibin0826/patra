package com.patra.starter.feign.error.util;

import com.patra.starter.feign.error.exception.RemoteCallException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RemoteErrorHelper utility class.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class RemoteErrorHelperTest {
    
    /**
     * Test isNotFound with 404 HTTP status.
     */
    @Test
    void shouldDetectNotFoundByHttpStatus() {
        // Given
        RemoteCallException exception = new RemoteCallException(404, "Not Found", "TestClient#getResource()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.isNotFound(exception)).isTrue();
    }
    
    /**
     * Test isNotFound with error code ending in -0404.
     */
    @Test
    void shouldDetectNotFoundByErrorCode() {
        // Given - Create ProblemDetail with error code
        ProblemDetail problemDetail = ProblemDetail.forStatus(422);
        problemDetail.setDetail("REG-0404");
        problemDetail.setProperty("code", "REG-0404");
        RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#getResource()");
        
        // When & Then
        assertThat(RemoteErrorHelper.isNotFound(exception)).isTrue();
    }
    
    /**
     * Test isNotFound returns false for non-404 scenarios.
     */
    @Test
    void shouldNotDetectNotFoundForOtherErrors() {
        // Given
        RemoteCallException exception = new RemoteCallException(409, "Conflict", "TestClient#createResource()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.isNotFound(exception)).isFalse();
    }
    
    /**
     * Test isConflict with 409 HTTP status.
     */
    @Test
    void shouldDetectConflictByHttpStatus() {
        // Given
        RemoteCallException exception = new RemoteCallException(409, "Conflict", "TestClient#createResource()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.isConflict(exception)).isTrue();
    }
    
    /**
     * Test isConflict with error code ending in -0409.
     */
    @Test
    void shouldDetectConflictByErrorCode() {
        // Given - Create ProblemDetail with error code
        ProblemDetail problemDetail = ProblemDetail.forStatus(422);
        problemDetail.setDetail("REG-0409");
        problemDetail.setProperty("code", "REG-0409");
        RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#createResource()");
        
        // When & Then
        assertThat(RemoteErrorHelper.isConflict(exception)).isTrue();
    }
    
    /**
     * Test isConflict returns false for non-409 scenarios.
     */
    @Test
    void shouldNotDetectConflictForOtherErrors() {
        // Given
        RemoteCallException exception = new RemoteCallException(404, "Not Found", "TestClient#getResource()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.isConflict(exception)).isFalse();
    }
    
    /**
     * Test isClientError for 4xx status codes.
     */
    @Test
    void shouldDetectClientErrors() {
        // Given
        RemoteCallException exception400 = new RemoteCallException(400, "Bad Request", "TestClient#method()", null);
        RemoteCallException exception404 = new RemoteCallException(404, "Not Found", "TestClient#method()", null);
        RemoteCallException exception422 = new RemoteCallException(422, "Unprocessable Entity", "TestClient#method()", null);
        RemoteCallException exception499 = new RemoteCallException(499, "Client Closed Request", "TestClient#method()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.isClientError(exception400)).isTrue();
        assertThat(RemoteErrorHelper.isClientError(exception404)).isTrue();
        assertThat(RemoteErrorHelper.isClientError(exception422)).isTrue();
        assertThat(RemoteErrorHelper.isClientError(exception499)).isTrue();
    }
    
    /**
     * Test isClientError returns false for non-4xx status codes.
     */
    @Test
    void shouldNotDetectClientErrorForServerErrors() {
        // Given
        RemoteCallException exception500 = new RemoteCallException(500, "Internal Server Error", "TestClient#method()", null);
        RemoteCallException exception200 = new RemoteCallException(200, "OK", "TestClient#method()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.isClientError(exception500)).isFalse();
        assertThat(RemoteErrorHelper.isClientError(exception200)).isFalse();
    }
    
    /**
     * Test is() method with matching error code.
     */
    @Test
    void shouldMatchSpecificErrorCode() {
        // Given - Create ProblemDetail with error code
        ProblemDetail problemDetail = ProblemDetail.forStatus(422);
        problemDetail.setDetail("REG-1001");
        problemDetail.setProperty("code", "REG-1001");
        RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#method()");
        
        // When & Then
        assertThat(RemoteErrorHelper.is(exception, "REG-1001")).isTrue();
    }
    
    /**
     * Test is() method with non-matching error code.
     */
    @Test
    void shouldNotMatchDifferentErrorCode() {
        // Given - Create ProblemDetail with error code
        ProblemDetail problemDetail = ProblemDetail.forStatus(422);
        problemDetail.setDetail("REG-1001");
        problemDetail.setProperty("code", "REG-1001");
        RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#method()");
        
        // When & Then
        assertThat(RemoteErrorHelper.is(exception, "REG-1002")).isFalse();
    }
    
    /**
     * Test is() method with null error code.
     */
    @Test
    void shouldNotMatchWhenErrorCodeIsNull() {
        // Given
        RemoteCallException exception = new RemoteCallException(500, "Internal Server Error", "TestClient#method()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.is(exception, "REG-1001")).isFalse();
    }
    
    /**
     * Test hasErrorCode with non-null error code.
     */
    @Test
    void shouldDetectPresenceOfErrorCode() {
        // Given - Create ProblemDetail with error code
        ProblemDetail problemDetail = ProblemDetail.forStatus(422);
        problemDetail.setDetail("REG-1001");
        problemDetail.setProperty("code", "REG-1001");
        RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#method()");
        
        // When & Then
        assertThat(RemoteErrorHelper.hasErrorCode(exception)).isTrue();
    }
    
    /**
     * Test hasErrorCode with null error code.
     */
    @Test
    void shouldDetectAbsenceOfErrorCode() {
        // Given
        RemoteCallException exception = new RemoteCallException(500, "Internal Server Error", "TestClient#method()", null);
        
        // When & Then
        assertThat(RemoteErrorHelper.hasErrorCode(exception)).isFalse();
    }
    
    /**
     * Test hasErrorCode with empty error code.
     */
    @Test
    void shouldDetectEmptyErrorCode() {
        // Given - Create ProblemDetail with empty error code
        ProblemDetail problemDetail = ProblemDetail.forStatus(500);
        problemDetail.setDetail("Internal Server Error");
        problemDetail.setProperty("code", "");
        RemoteCallException exception = new RemoteCallException(problemDetail, "TestClient#method()");
        
        // When & Then
        assertThat(RemoteErrorHelper.hasErrorCode(exception)).isFalse();
    }
}