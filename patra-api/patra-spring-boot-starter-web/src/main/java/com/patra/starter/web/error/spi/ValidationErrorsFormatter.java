package com.patra.starter.web.error.spi;

import com.patra.starter.web.error.model.ValidationError;
import java.util.List;
import org.springframework.validation.BindingResult;

/**
 * 格式化验证错误的 SPI 契约,同时掩码敏感值并强制输出限制。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ValidationErrorsFormatter {

  /**
   * 将 {@link BindingResult} 转换为已掩码的验证错误列表。
   *
   * @param bindingResult 验证错误的来源
   * @return 已清理的验证错误,适合暴露给客户端
   */
  List<ValidationError> formatWithMasking(BindingResult bindingResult);
}
