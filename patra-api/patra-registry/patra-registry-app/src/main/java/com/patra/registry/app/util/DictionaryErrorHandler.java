package com.patra.registry.app.util;

import com.patra.registry.domain.exception.DictionaryDomainException;
import com.patra.registry.domain.exception.DictionaryNotFoundException;
import com.patra.registry.domain.exception.DictionaryValidationException;
import com.patra.registry.domain.exception.DictionaryRepositoryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Utility class for consistent error handling across dictionary operations.
 * Provides standardized error handling patterns, exception wrapping, and
 * logging strategies
 * for all dictionary-related operations in the application layer.
 * 
 * <p>
 * This utility ensures consistent error handling behavior across all dictionary
 * services
 * and provides centralized exception management with proper logging and context
 * preservation.
 * </p>
 * 
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Standardized exception wrapping and re-throwing</li>
 * <li>Consistent logging patterns for different error types</li>
 * <li>Context preservation for debugging and monitoring</li>
 * <li>Error classification and appropriate response handling</li>
 * </ul>
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class DictionaryErrorHandler {

    /**
     * Executes a dictionary operation with standardized error handling.
     * Wraps the operation execution with consistent exception handling and logging.
     * 
     * @param operation     the operation to execute
     * @param operationName the name of the operation for logging purposes
     * @param typeCode      the dictionary type code associated with the operation,
     *                      if applicable
     * @param itemCode      the dictionary item code associated with the operation,
     *                      if applicable
     * @param <T>           the return type of the operation
     * @return the result of the operation
     * @throws DictionaryDomainException     if a domain-level error occurs
     * @throws DictionaryRepositoryException if an infrastructure-level error occurs
     */
    public <T> T executeWithErrorHandling(Supplier<T> operation, String operationName,
            String typeCode, String itemCode) {
        log.debug("Executing dictionary operation: operation={}, typeCode={}, itemCode={}",
                operationName, typeCode, itemCode);

        try {
            T result = operation.get();
            log.debug("Dictionary operation completed successfully: operation={}, typeCode={}, itemCode={}",
                    operationName, typeCode, itemCode);
            return result;

        } catch (DictionaryNotFoundException e) {
            log.info("Dictionary resource not found: operation={}, typeCode={}, itemCode={}, message={}",
                    operationName, typeCode, itemCode, e.getMessage());
            throw e; // Re-throw as-is for proper handling by controllers

        } catch (DictionaryValidationException e) {
            log.warn("Dictionary validation failed: operation={}, typeCode={}, itemCode={}, errors={}",
                    operationName, typeCode, itemCode, e.getValidationErrors());
            throw e; // Re-throw as-is for proper handling by controllers

        } catch (DictionaryDomainException e) {
            log.warn("Dictionary domain error: operation={}, typeCode={}, itemCode={}, message={}",
                    operationName, typeCode, itemCode, e.getMessage());
            throw e; // Re-throw as-is for proper handling by controllers

        } catch (DictionaryRepositoryException e) {
            log.error("Dictionary repository error: operation={}, typeCode={}, itemCode={}, message={}",
                    operationName, typeCode, itemCode, e.getMessage(), e);
            throw e; // Re-throw as-is for proper handling by controllers

        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for dictionary operation: operation={}, typeCode={}, itemCode={}, message={}",
                    operationName, typeCode, itemCode, e.getMessage());
            throw e; // Re-throw as-is for proper handling by controllers

        } catch (Exception e) {
            log.error("Unexpected error in dictionary operation: operation={}, typeCode={}, itemCode={}, message={}",
                    operationName, typeCode, itemCode, e.getMessage(), e);

            // Wrap unexpected exceptions in domain exception for consistent handling
            throw new DictionaryDomainException(
                    String.format("Unexpected error in %s operation", operationName),
                    typeCode, itemCode, e);
        }
    }

    /**
     * Executes a dictionary operation without type/item context.
     * Simplified version for operations that don't involve specific dictionary
     * items.
     * 
     * @param operation     the operation to execute
     * @param operationName the name of the operation for logging purposes
     * @param <T>           the return type of the operation
     * @return the result of the operation
     * @throws DictionaryDomainException     if a domain-level error occurs
     * @throws DictionaryRepositoryException if an infrastructure-level error occurs
     */
    public <T> T executeWithErrorHandling(Supplier<T> operation, String operationName) {
        return executeWithErrorHandling(operation, operationName, null, null);
    }

    /**
     * Validates input parameters and throws appropriate exceptions for null or
     * empty values.
     * Provides consistent parameter validation across all dictionary operations.
     * 
     * @param typeCode the dictionary type code to validate
     * @param itemCode the dictionary item code to validate, can be null for
     *                 type-only operations
     * @throws IllegalArgumentException if typeCode is null or empty, or if itemCode
     *                                  is provided but null/empty
     */
    public void validateParameters(String typeCode, String itemCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }

        if (itemCode != null && itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be empty when provided");
        }
    }

    /**
     * Validates that a type code parameter is not null or empty.
     * 
     * @param typeCode the dictionary type code to validate
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public void validateTypeCode(String typeCode) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
    }

    /**
     * Validates that an item code parameter is not null or empty.
     * 
     * @param itemCode the dictionary item code to validate
     * @throws IllegalArgumentException if itemCode is null or empty
     */
    public void validateItemCode(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
    }

    /**
     * Validates that a list parameter is not null.
     * 
     * @param list          the list to validate
     * @param parameterName the name of the parameter for error messages
     * @throws IllegalArgumentException if the list is null
     */
    public void validateListParameter(List<?> list, String parameterName) {
        if (list == null) {
            throw new IllegalArgumentException(parameterName + " cannot be null");
        }
    }

    /**
     * Creates a DictionaryNotFoundException for missing dictionary types.
     * 
     * @param typeCode the dictionary type code that was not found
     * @return a new DictionaryNotFoundException with appropriate message
     */
    public DictionaryNotFoundException createTypeNotFoundException(String typeCode) {
        return new DictionaryNotFoundException(typeCode);
    }

    /**
     * Creates a DictionaryNotFoundException for missing dictionary items.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code that was not found
     * @return a new DictionaryNotFoundException with appropriate message
     */
    public DictionaryNotFoundException createItemNotFoundException(String typeCode, String itemCode) {
        return new DictionaryNotFoundException(typeCode, itemCode);
    }

    /**
     * Creates a DictionaryValidationException for validation failures.
     * 
     * @param message  the validation error message
     * @param typeCode the dictionary type code associated with the validation
     *                 failure
     * @param itemCode the dictionary item code associated with the validation
     *                 failure
     * @return a new DictionaryValidationException with appropriate context
     */
    public DictionaryValidationException createValidationException(String message, String typeCode, String itemCode) {
        return new DictionaryValidationException(message, typeCode, itemCode);
    }

    /**
     * Creates a DictionaryValidationException for multiple validation failures.
     * 
     * @param validationErrors list of validation error messages
     * @param typeCode         the dictionary type code associated with the
     *                         validation failures
     * @return a new DictionaryValidationException with multiple errors
     */
    public DictionaryValidationException createValidationException(List<String> validationErrors, String typeCode) {
        return new DictionaryValidationException(validationErrors, typeCode);
    }

    /**
     * Creates a DictionaryRepositoryException for infrastructure failures.
     * 
     * @param message   the error message
     * @param operation the repository operation that failed
     * @param cause     the underlying cause of the failure
     * @return a new DictionaryRepositoryException with appropriate context
     */
    public DictionaryRepositoryException createRepositoryException(String message, String operation, Throwable cause) {
        return new DictionaryRepositoryException(message, operation, cause);
    }

    /**
     * Logs a successful dictionary operation at the appropriate level.
     * 
     * @param operationName     the name of the operation
     * @param typeCode          the dictionary type code, if applicable
     * @param itemCode          the dictionary item code, if applicable
     * @param additionalContext additional context information
     */
    public void logSuccessfulOperation(String operationName, String typeCode, String itemCode,
            String additionalContext) {
        if (isHighFrequencyOperation(operationName)) {
            log.debug("Dictionary operation completed: operation={}, typeCode={}, itemCode={}, context={}",
                    operationName, typeCode, itemCode, additionalContext);
        } else {
            log.info("Dictionary operation completed: operation={}, typeCode={}, itemCode={}, context={}",
                    operationName, typeCode, itemCode, additionalContext);
        }
    }

    /**
     * Determines if an operation is high-frequency to avoid log flooding.
     * 
     * @param operationName the name of the operation
     * @return true if the operation is considered high-frequency
     */
    private boolean isHighFrequencyOperation(String operationName) {
        return operationName != null && (operationName.contains("findItem") ||
                operationName.contains("validateReference") ||
                operationName.contains("existsBy"));
    }
}