package com.patra.starter.core.error.strategy;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于错误码后缀的 HTTP 状态映射策略。
 *
 * <p>约定：业务错误码形如 {@code REG-0404}，连字符后的 4 位数字即对应 HTTP 状态码。
 * 因此可通过提取后缀进行快速推断，例如：{@code REG-0404 -> 404}。
 *
 * <p>注意：若无法从错误码中解析出合法的状态码（非 100~599 或非数字），将回退为 500。
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.core.error.spi.StatusMappingStrategy 状态映射 SPI
 */
@Slf4j
public class SuffixHeuristicStatusMappingStrategy implements StatusMappingStrategy {
    
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        if (errorCode == null) {
            // Missing error code, fallback to 500
            log.debug("Error code is null, defaulting to 500");
            return 500;
        }
        
        String code = errorCode.code();
        if (code == null || code.isEmpty()) {
            // Missing error code string, fallback to 500
            log.debug("Error code string is null or empty, defaulting to 500");
            return 500;
        }
        
        // 提取最后一个连字符后的后缀（例如：REG-0404 → 0404）
        int lastDashIndex = code.lastIndexOf('-');
        if (lastDashIndex == -1 || lastDashIndex == code.length() - 1) {
            log.debug("Error code '{}' has no valid suffix, defaulting to 500", code);
            return 500;
        }
        
        String suffix = code.substring(lastDashIndex + 1);
        
        try {
            int status = Integer.parseInt(suffix);
            
            // Validate HTTP status code range
            if (status < 100 || status > 599) {
                log.debug("Parsed status {} from '{}' is out of valid range, defaulting to 500", status, code);
                return 500;
            }
            
            log.debug("Mapped error code '{}' to HTTP status {}", code, status);
            return status;
            
        } catch (NumberFormatException e) {
            log.debug("Cannot parse suffix '{}' from error code '{}' as integer, defaulting to 500", suffix, code);
            return 500;
        }
    }
}
