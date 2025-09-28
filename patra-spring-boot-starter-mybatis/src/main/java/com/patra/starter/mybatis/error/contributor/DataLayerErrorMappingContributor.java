package com.patra.starter.mybatis.error.contributor;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

/**
 * MyBatis-Plus/数据库层异常的错误映射贡献者。
 *
 * <p>通过统一的错误解析通道，将 MyBatis-Plus 及数据库异常映射为合适的业务错误码。</p>
 */
@Slf4j
@Component
public class DataLayerErrorMappingContributor implements ErrorMappingContributor {

    private final HttpStdErrors.Group http;

    public DataLayerErrorMappingContributor(HttpStdErrors.Group http) {
        this.http = http;
    }

    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // MyBatis-Plus 异常 → 500
        if (exception instanceof MybatisPlusException) {
            log.debug("Mapping MyBatis-Plus exception to server error: exception={}",
                    exception.getClass().getSimpleName());
            return Optional.of(http.INTERNAL_ERROR());
        }

        // 约束类异常 → 409
        if (exception instanceof SQLIntegrityConstraintViolationException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            if ("23000".equals(sqlState)) { // MySQL duplicate entry
                log.debug("Mapping SQL duplicate key violation to conflict: sqlState={}", sqlState);
                return Optional.of(http.CONFLICT());
            }
            log.debug("Mapping SQL integrity constraint violation to conflict: sqlState={}", sqlState);
            return Optional.of(http.CONFLICT());
        }

        // 通用 SQL 异常分类
        if (exception instanceof SQLException sqlEx) {
            String sqlState = sqlEx.getSQLState();
            int errorCode = sqlEx.getErrorCode();

            // MySQL 常见错误码 → 409
            if (errorCode == 1062) { // Duplicate entry
                log.debug("Mapping MySQL duplicate entry error to conflict: errorCode={}", errorCode);
                return Optional.of(http.CONFLICT());
            }
            if (errorCode == 1452) { // Foreign key constraint fails
                log.debug("Mapping MySQL foreign key constraint error to conflict: errorCode={}", errorCode);
                return Optional.of(http.CONFLICT());
            }
            if (errorCode == 1451) { // Cannot delete or update a parent row
                log.debug("Mapping MySQL parent row constraint error to conflict: errorCode={}", errorCode);
                return Optional.of(http.CONFLICT());
            }

            // 连接/超时类 → 503
            if (sqlState != null && (sqlState.startsWith("08") || sqlState.startsWith("HY"))) {
                log.debug("Mapping SQL connection/timeout error to service unavailable: sqlState={}", sqlState);
                return Optional.of(http.UNAVAILABLE());
            }

            // 其它 → 500
            log.debug("Mapping general SQL exception to server error: sqlState={}, errorCode={}", sqlState, errorCode);
            return Optional.of(http.INTERNAL_ERROR());
        }

        return Optional.empty();
    }
}
