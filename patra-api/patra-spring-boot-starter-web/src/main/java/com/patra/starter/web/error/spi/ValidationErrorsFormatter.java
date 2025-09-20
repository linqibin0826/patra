package com.patra.starter.web.error.spi;

import com.patra.starter.web.error.model.ValidationError;
import org.springframework.validation.BindingResult;
import java.util.List;

/**
 * 校验错误格式化 SPI 接口（需支持敏感信息脱敏与数量限制）。
 *
 * <p>实现类应对敏感字段值进行掩码处理，并控制最大输出条数。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ValidationErrorsFormatter {
    
    /**
     * 从 Spring 的 BindingResult 格式化校验错误（带脱敏）。
     *
     * @param bindingResult 包含校验错误的结果对象
     * @return 脱敏后的校验错误列表
     */
    List<ValidationError> formatWithMasking(BindingResult bindingResult);
}
