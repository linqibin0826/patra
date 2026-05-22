package dev.linqibin.starter.web.error.formatter;

import dev.linqibin.starter.web.error.model.ValidationError;
import dev.linqibin.starter.web.error.spi.ValidationErrorsFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/// 默认的 {@link ValidationErrorsFormatter}，用于掩盖敏感值并限制返回给客户端的验证错误数量。
///
/// @author linqibin
/// @since 0.1.0
/// @see dev.linqibin.starter.web.error.model.ValidationError
@Slf4j
public class DefaultValidationErrorsFormatter implements ValidationErrorsFormatter {

  /// 响应中包含的验证错误的最大数量。
  private static final int MAX_ERRORS = 100;

  /// 应被掩盖的字段名称模式（不区分大小写，子字符串匹配）。
  private static final Set<String> SENSITIVE_PATTERNS =
      Set.of(
          "password",
          "token",
          "secret",
          "key",
          "credential",
          "auth",
          "pin",
          "ssn",
          "credit",
          "card",
          "account");

  /// 格式化验证错误并掩码敏感字段值。
  ///
  /// @param bindingResult Spring 验证绑定结果
  /// @return 格式化并掩码后的验证错误列表
  @Override
  public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
    log.debug("Formatting validation errors: errorCount={}", bindingResult.getErrorCount());

    List<ValidationError> errors =
        bindingResult.getAllErrors().stream()
            .limit(MAX_ERRORS)
            .map(this::mapToValidationError)
            .collect(Collectors.toList());

    if (bindingResult.getErrorCount() > MAX_ERRORS) {
      log.warn(
          "Validation errors truncated: total={}, included={}",
          bindingResult.getErrorCount(),
          MAX_ERRORS);
    }

    return errors;
  }

  /// 将 Spring 的 {@link ObjectError} 映射到 {@link ValidationError} 并掩盖敏感值。
  ///
  /// @param error Spring 验证报告的绑定错误
  /// @return 清理后的验证错误
  private ValidationError mapToValidationError(ObjectError error) {
    if (error instanceof FieldError fieldError) {
      String fieldName = fieldError.getField();
      Object rejectedValue = maskSensitiveValue(fieldName, fieldError.getRejectedValue());
      String message = fieldError.getDefaultMessage();

      return new ValidationError(fieldName, rejectedValue, message);
    } else {
      // 全局错误（非字段特定）
      return new ValidationError(error.getObjectName(), null, error.getDefaultMessage());
    }
  }

  /// 基于配置的名称模式掩盖敏感的字段值。
  ///
  /// @param fieldName 逻辑字段名
  /// @param value 客户端提供的被拒绝值
  /// @return 当被认为是敏感时的掩盖值；否则为原始值
  private Object maskSensitiveValue(String fieldName, Object value) {
    if (fieldName == null || value == null) {
      return value;
    }

    String lowerFieldName = fieldName.toLowerCase(Locale.ROOT);
    boolean isSensitive = SENSITIVE_PATTERNS.stream().anyMatch(lowerFieldName::contains);

    if (isSensitive) {
      log.debug("Masking sensitive field: fieldName={}", fieldName);
      return "***";
    }

    return value;
  }
}
