package com.patra.starter.web.error.formatter;

import com.patra.starter.web.error.model.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DefaultValidationErrorsFormatter class.
 * Tests the formatting of validation errors with sensitive data masking and size limits.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DefaultValidationErrorsFormatterTest {
    
    private DefaultValidationErrorsFormatter formatter;
    
    @BeforeEach
    void setUp() {
        formatter = new DefaultValidationErrorsFormatter();
    }
    
    /**
     * Test formatting validation errors with normal fields.
     */
    @Test
    void shouldFormatValidationErrorsWithNormalFields() {
        // Given
        TestUser user = new TestUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
        bindingResult.addError(new FieldError("user", "name", "John", false, null, null, "Name is required"));
        bindingResult.addError(new FieldError("user", "email", "invalid-email", false, null, null, "Email format is invalid"));
        
        // When
        List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
        
        // Then
        assertThat(errors).hasSize(2);
        
        ValidationError nameError = errors.get(0);
        assertThat(nameError.field()).isEqualTo("name");
        assertThat(nameError.rejectedValue()).isEqualTo("John");
        assertThat(nameError.message()).isEqualTo("Name is required");
        
        ValidationError emailError = errors.get(1);
        assertThat(emailError.field()).isEqualTo("email");
        assertThat(emailError.rejectedValue()).isEqualTo("invalid-email");
        assertThat(emailError.message()).isEqualTo("Email format is invalid");
    }
    
    /**
     * Test formatting validation errors with sensitive fields are masked.
     */
    @Test
    void shouldMaskSensitiveFieldValues() {
        // Given
        TestUser user = new TestUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
        bindingResult.addError(new FieldError("user", "password", "secret123", false, null, null, "Password too weak"));
        bindingResult.addError(new FieldError("user", "token", "abc123token", false, null, null, "Token expired"));
        bindingResult.addError(new FieldError("user", "apiKey", "key456", false, null, null, "API key invalid"));
        bindingResult.addError(new FieldError("user", "secret", "mysecret", false, null, null, "Secret format invalid"));
        
        // When
        List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
        
        // Then
        assertThat(errors).hasSize(4);
        
        // All sensitive fields should be masked
        errors.forEach(error -> {
            assertThat(error.rejectedValue()).isEqualTo("***");
        });
        
        assertThat(errors.get(0).field()).isEqualTo("password");
        assertThat(errors.get(1).field()).isEqualTo("token");
        assertThat(errors.get(2).field()).isEqualTo("apiKey");
        assertThat(errors.get(3).field()).isEqualTo("secret");
    }
    
    /**
     * Test formatting validation errors with mixed sensitive and normal fields.
     */
    @Test
    void shouldMaskOnlySensitiveFields() {
        // Given
        TestUser user = new TestUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
        bindingResult.addError(new FieldError("user", "name", "John", false, null, null, "Name is required"));
        bindingResult.addError(new FieldError("user", "password", "secret123", false, null, null, "Password too weak"));
        bindingResult.addError(new FieldError("user", "email", "test@example.com", false, null, null, "Email already exists"));
        
        // When
        List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
        
        // Then
        assertThat(errors).hasSize(3);
        
        ValidationError nameError = errors.stream()
            .filter(e -> "name".equals(e.field()))
            .findFirst()
            .orElseThrow();
        assertThat(nameError.rejectedValue()).isEqualTo("John");
        
        ValidationError passwordError = errors.stream()
            .filter(e -> "password".equals(e.field()))
            .findFirst()
            .orElseThrow();
        assertThat(passwordError.rejectedValue()).isEqualTo("***");
        
        ValidationError emailError = errors.stream()
            .filter(e -> "email".equals(e.field()))
            .findFirst()
            .orElseThrow();
        assertThat(emailError.rejectedValue()).isEqualTo("test@example.com");
    }
    
    /**
     * Test formatting validation errors with null rejected values.
     */
    @Test
    void shouldHandleNullRejectedValues() {
        // Given
        TestUser user = new TestUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
        bindingResult.addError(new FieldError("user", "name", null, false, null, null, "Name is required"));
        bindingResult.addError(new FieldError("user", "password", null, false, null, null, "Password is required"));
        
        // When
        List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
        
        // Then
        assertThat(errors).hasSize(2);
        
        ValidationError nameError = errors.get(0);
        assertThat(nameError.field()).isEqualTo("name");
        assertThat(nameError.rejectedValue()).isNull();
        assertThat(nameError.message()).isEqualTo("Name is required");
        
        ValidationError passwordError = errors.get(1);
        assertThat(passwordError.field()).isEqualTo("password");
        assertThat(passwordError.rejectedValue()).isNull(); // null values are not masked
        assertThat(passwordError.message()).isEqualTo("Password is required");
    }
    
    /**
     * Test formatting validation errors respects size limit.
     */
    @Test
    void shouldRespectSizeLimit() {
        // Given
        TestUser user = new TestUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
        
        // Add more than 100 errors
        for (int i = 0; i < 150; i++) {
            bindingResult.addError(new FieldError("user", "field" + i, "value" + i, false, null, null, "Error " + i));
        }
        
        // When
        List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
        
        // Then
        assertThat(errors).hasSize(100); // Should be limited to 100
        assertThat(errors.get(0).field()).isEqualTo("field0");
        assertThat(errors.get(99).field()).isEqualTo("field99");
    }
    
    /**
     * Test formatting empty binding result.
     */
    @Test
    void shouldHandleEmptyBindingResult() {
        // Given
        TestUser user = new TestUser();
        BindingResult bindingResult = new BeanPropertyBindingResult(user, "user");
        
        // When
        List<ValidationError> errors = formatter.formatWithMasking(bindingResult);
        
        // Then
        assertThat(errors).isEmpty();
    }
    
    /**
     * Test user class for testing purposes.
     */
    private static class TestUser {
        private String name;
        private String email;
        private String password;
        private String token;
        private String apiKey;
        private String secret;
        
        // Getters and setters would be here in real implementation
    }
}