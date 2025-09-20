package com.patra.registry.config;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.registry.api.error.RegistryErrorCode;
import com.patra.registry.domain.exception.*;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Registry 专用错误映射贡献者：提供细粒度的异常→错误码映射。
 *
 * <p>覆盖 Registry 领域异常与数据层异常的显式映射。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

    /**
     * 执行异常到错误码的映射。
     */
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        log.debug("Mapping exception: {}", exception.getClass().getSimpleName());
        
        // Dictionary-specific exceptions
        if (exception instanceof DictionaryNotFoundException ex) {
            // Check if it's a type-level or item-level not found
            if (ex.getItemCode() == null) {
                return Optional.of(RegistryErrorCode.REG_1401); // Dictionary Type Not Found
            } else {
                return Optional.of(RegistryErrorCode.REG_1402); // Dictionary Item Not Found
            }
        }
        
        if (exception instanceof DictionaryItemDisabled) {
            return Optional.of(RegistryErrorCode.REG_1403);
        }
        
        if (exception instanceof DictionaryTypeAlreadyExists) {
            return Optional.of(RegistryErrorCode.REG_1404);
        }
        
        if (exception instanceof DictionaryItemAlreadyExists) {
            return Optional.of(RegistryErrorCode.REG_1405);
        }
        
        if (exception instanceof DictionaryTypeDisabled) {
            return Optional.of(RegistryErrorCode.REG_1406);
        }
        
        if (exception instanceof DictionaryValidationException) {
            return Optional.of(RegistryErrorCode.REG_1407);
        }
        
        if (exception instanceof DictionaryDefaultItemMissing) {
            return Optional.of(RegistryErrorCode.REG_1408);
        }
        
        if (exception instanceof DictionaryRepositoryException) {
            return Optional.of(RegistryErrorCode.REG_1409);
        }
        
        // Registry general exceptions
        if (exception instanceof RegistryQuotaExceeded) {
            return Optional.of(RegistryErrorCode.REG_1501);
        }
        
        // Data layer exceptions - map to appropriate business codes
        if (exception instanceof DuplicateKeyException) {
            // Could be dictionary type or item already exists
            return Optional.of(RegistryErrorCode.REG_0409); // Conflict
        }
        
        if (exception instanceof DataIntegrityViolationException) {
            // Data integrity issues - validation error
            return Optional.of(RegistryErrorCode.REG_0422); // Unprocessable Entity
        }
        
        if (exception instanceof OptimisticLockingFailureException) {
            // Concurrent modification - conflict
            return Optional.of(RegistryErrorCode.REG_0409); // Conflict
        }
        
        // No mapping found
        log.debug("No mapping found for exception: {}", exception.getClass().getSimpleName());
        return Optional.empty();
    }
}
