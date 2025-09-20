package com.patra.starter.mybatis.error.contributor;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

/**
 * Error mapping contributor for MyBatis-Plus and database layer exceptions.
 * Maps MyBatis-Plus and database exceptions to appropriate error codes through the unified error resolution channel.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class DataLayerErrorMappingContributor implements ErrorMappingContributor {
    
    private final ErrorProperties errorProperties;
    
    public DataLayerErrorMappingContributor(ErrorProperties errorProperties) {
        this.errorProperties = errorProperties;
    }
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        String contextPrefix = errorProperties.getContextPrefix();
        if (contextPrefix == null) {
            log.warn("Context prefix not configured, cannot map data layer exception: {}", 
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
        
        // MyBatis-Plus exceptions
        if (exception instanceof MybatisPlusException) {
            log.debug("Mapping MyBatis-Plus exception to server error: exception={}", 
                    exception.getClass().getSimpleName());
            return Optional.of(() -> contextPrefix + "-0500");
        }
        
        // SQL integrity constraint violations (duplicate key, foreign key, etc.)
        if (exception instanceof SQLIntegrityConstraintViolationException) {
            SQLIntegrityConstraintViolationException sqlEx = (SQLIntegrityConstraintViolationException) exception;
            String sqlState = sqlEx.getSQLState();
            
            // MySQL duplicate entry error (23000)
            if ("23000".equals(sqlState)) {
                log.debug("Mapping SQL duplicate key violation to conflict: sqlState={}", sqlState);
                return Optional.of(() -> contextPrefix + "-0409");
            }
            
            log.debug("Mapping SQL integrity constraint violation to conflict: sqlState={}", sqlState);
            return Optional.of(() -> contextPrefix + "-0409");
        }
        
        // General SQL exceptions
        if (exception instanceof SQLException) {
            SQLException sqlEx = (SQLException) exception;
            String sqlState = sqlEx.getSQLState();
            int errorCode = sqlEx.getErrorCode();
            
            // MySQL specific error codes
            if (errorCode == 1062) { // Duplicate entry
                log.debug("Mapping MySQL duplicate entry error to conflict: errorCode={}", errorCode);
                return Optional.of(() -> contextPrefix + "-0409");
            }
            
            if (errorCode == 1452) { // Foreign key constraint fails
                log.debug("Mapping MySQL foreign key constraint error to conflict: errorCode={}", errorCode);
                return Optional.of(() -> contextPrefix + "-0409");
            }
            
            if (errorCode == 1451) { // Cannot delete or update a parent row
                log.debug("Mapping MySQL parent row constraint error to conflict: errorCode={}", errorCode);
                return Optional.of(() -> contextPrefix + "-0409");
            }
            
            // Connection/timeout related errors
            if (sqlState != null && (sqlState.startsWith("08") || sqlState.startsWith("HY"))) {
                log.debug("Mapping SQL connection/timeout error to service unavailable: sqlState={}", sqlState);
                return Optional.of(() -> contextPrefix + "-0503");
            }
            
            log.debug("Mapping general SQL exception to server error: sqlState={}, errorCode={}", 
                     sqlState, errorCode);
            return Optional.of(() -> contextPrefix + "-0500");
        }
        
        return Optional.empty();
    }
}