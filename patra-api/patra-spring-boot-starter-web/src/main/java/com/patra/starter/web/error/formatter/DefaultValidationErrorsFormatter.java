package com.patra.starter.web.error.formatter;

import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认的校验错误格式化器，带敏感字段脱敏与数量限制。
 *
 * <p>功能：对常见敏感字段进行掩码处理，并对错误条数施加上限，避免响应体过大。</p>
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.model.ValidationError
 */
@Slf4j
public class DefaultValidationErrorsFormatter implements ValidationErrorsFormatter {
    
    /** 响应中包含的校验错误上限 */
    private static final int MAX_ERRORS = 100;
    
    /** 需要脱敏的字段名模式（忽略大小写，包含匹配） */
    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
        "password", "token", "secret", "key", "credential", "auth", 
        "pin", "ssn", "credit", "card", "account"
    );
    
    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        log.debug("Formatting validation errors: errorCount={}", bindingResult.getErrorCount());
        
        List<ValidationError> errors = bindingResult.getAllErrors().stream()
            .limit(MAX_ERRORS)
            .map(this::mapToValidationError)
            .collect(Collectors.toList());
        
        if (bindingResult.getErrorCount() > MAX_ERRORS) {
            log.warn("Validation errors truncated: total={}, included={}", 
                    bindingResult.getErrorCount(), MAX_ERRORS);
        }
        
        return errors;
    }
    
    /**
     * 将 Spring 的 ObjectError 映射为 ValidationError（带脱敏）。
     *
     * @param error Spring 校验错误对象
     * @return 转换后的校验错误
     */
    private ValidationError mapToValidationError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            String fieldName = fieldError.getField();
            Object rejectedValue = maskSensitiveValue(fieldName, fieldError.getRejectedValue());
            String message = fieldError.getDefaultMessage();
            
            return new ValidationError(fieldName, rejectedValue, message);
        } else {
            // Global errors (not field-specific)
            return new ValidationError(
                error.getObjectName(), 
                null, 
                error.getDefaultMessage()
            );
        }
    }
    
    /**
     * 根据字段名模式对敏感值进行脱敏。
     *
     * @param fieldName 字段名
     * @param value 字段值
     * @return 脱敏后的值；非敏感则原样返回
     */
    private Object maskSensitiveValue(String fieldName, Object value) {
        if (fieldName == null || value == null) {
            return value;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        boolean isSensitive = SENSITIVE_PATTERNS.stream()
            .anyMatch(lowerFieldName::contains);
        
        if (isSensitive) {
            log.debug("Masking sensitive field: fieldName={}", fieldName);
            return "***";
        }
        
        return value;
    }
}
