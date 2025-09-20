package com.patra.starter.mybatis.error.contributor;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.config.ErrorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DataLayerErrorMappingContributor class.
 * Tests the mapping of data layer exceptions to error codes.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class DataLayerErrorMappingContributorTest {
    
    @Mock
    private ErrorProperties errorProperties;
    
    private DataLayerErrorMappingContributor contributor;
    
    @BeforeEach
    void setUp() {
        when(errorProperties.getContextPrefix()).thenReturn("TEST");
        contributor = new DataLayerErrorMappingContributor(errorProperties);
    }
    
    /**
     * Test mapping MybatisPlusException to server error code.
     */
    @Test
    void shouldMapMybatisPlusExceptionToServerError() {
        // Given
        MybatisPlusException exception = new MybatisPlusException("MyBatis-Plus error");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("TEST-0500");
    }
    
    /**
     * Test mapping SQLIntegrityConstraintViolationException to conflict error code.
     */
    @Test
    void shouldMapSQLIntegrityConstraintViolationExceptionToConflict() {
        // Given
        SQLIntegrityConstraintViolationException exception = new SQLIntegrityConstraintViolationException("Duplicate entry", "23000");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("TEST-0409");
    }
    
    /**
     * Test mapping SQLException with MySQL duplicate entry error code.
     */
    @Test
    void shouldMapSQLExceptionWithDuplicateEntryErrorCode() {
        // Given
        SQLException exception = new SQLException("Duplicate entry", "23000", 1062);
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("TEST-0409");
    }
    
    /**
     * Test mapping non-data layer exception returns empty.
     */
    @Test
    void shouldReturnEmptyForNonDataLayerException() {
        // Given
        RuntimeException exception = new RuntimeException("Generic runtime exception");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    /**
     * Test mapping null exception returns empty.
     */
    @Test
    void shouldReturnEmptyForNullException() {
        // Given
        Throwable exception = null;
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    /**
     * Test mapping with different context prefix.
     */
    @Test
    void shouldUseCorrectContextPrefix() {
        // Given
        when(errorProperties.getContextPrefix()).thenReturn("REG");
        contributor = new DataLayerErrorMappingContributor(errorProperties);
        MybatisPlusException exception = new MybatisPlusException("MyBatis-Plus error");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("REG-0500");
    }
}