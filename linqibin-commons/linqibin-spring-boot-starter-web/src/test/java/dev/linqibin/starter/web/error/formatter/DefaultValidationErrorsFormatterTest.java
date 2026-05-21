package dev.linqibin.starter.web.error.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.linqibin.starter.web.error.model.ValidationError;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/// DefaultValidationErrorsFormatter 单元测试。
@DisplayName("DefaultValidationErrorsFormatter 单元测试")
class DefaultValidationErrorsFormatterTest {

  private DefaultValidationErrorsFormatter formatter;

  @BeforeEach
  void setUp() {
    formatter = new DefaultValidationErrorsFormatter();
  }

  @Test
  @DisplayName("应该格式化字段验证错误")
  void shouldFormatFieldValidationErrors() {
    // Given: 准备 BindingResult 包含字段错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("userRequest", "email", "invalid-email", false, null, null, "必须是有效的邮箱");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证格式化结果
    assertThat(result).hasSize(1);
    ValidationError error = result.get(0);
    assertThat(error.field()).isEqualTo("email");
    assertThat(error.rejectedValue()).isEqualTo("invalid-email");
    assertThat(error.message()).isEqualTo("必须是有效的邮箱");
  }

  @Test
  @DisplayName("应该格式化全局对象错误")
  void shouldFormatGlobalObjectErrors() {
    // Given: 准备 BindingResult 包含全局错误
    BindingResult bindingResult = mock(BindingResult.class);
    ObjectError objectError = new ObjectError("userRequest", "对象验证失败");

    when(bindingResult.getAllErrors()).thenReturn(List.of(objectError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证格式化结果
    assertThat(result).hasSize(1);
    ValidationError error = result.get(0);
    assertThat(error.field()).isEqualTo("userRequest");
    assertThat(error.rejectedValue()).isNull();
    assertThat(error.message()).isEqualTo("对象验证失败");
  }

  @Test
  @DisplayName("应该掩码敏感字段 - password")
  void shouldMaskSensitiveFieldPassword() {
    // Given: 准备包含 password 字段的错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("userRequest", "password", "secret123", false, null, null, "密码格式不正确");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证敏感字段被掩码
    assertThat(result).hasSize(1);
    ValidationError error = result.get(0);
    assertThat(error.rejectedValue()).isEqualTo("***");
  }

  @Test
  @DisplayName("应该掩码敏感字段 - token")
  void shouldMaskSensitiveFieldToken() {
    // Given: 准备包含 token 字段的错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("authRequest", "authToken", "abc123xyz", false, null, null, "令牌无效");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证敏感字段被掩码
    assertThat(result).hasSize(1);
    ValidationError error = result.get(0);
    assertThat(error.rejectedValue()).isEqualTo("***");
  }

  @Test
  @DisplayName("应该掩码敏感字段 - 不区分大小写")
  void shouldMaskSensitiveFieldCaseInsensitive() {
    // Given: 准备包含大小写混合敏感字段的错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("request", "UserPassword", "secret", false, null, null, "密码无效");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证敏感字段被掩码
    assertThat(result).hasSize(1);
    ValidationError error = result.get(0);
    assertThat(error.rejectedValue()).isEqualTo("***");
  }

  @Test
  @DisplayName("应该保留非敏感字段的原始值")
  void shouldPreserveNonSensitiveFieldValues() {
    // Given: 准备包含非敏感字段的错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("userRequest", "username", "invalid user", false, null, null, "用户名格式不正确");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证非敏感字段保留原始值
    assertThat(result).hasSize(1);
    ValidationError error = result.get(0);
    assertThat(error.rejectedValue()).isEqualTo("invalid user");
  }

  @Test
  @DisplayName("应该截断超过最大数量的验证错误")
  void shouldTruncateValidationErrorsExceedingMaximum() {
    // Given: 准备超过 100 个验证错误
    BindingResult bindingResult = mock(BindingResult.class);
    List<ObjectError> errors =
        IntStream.range(0, 150)
            .mapToObj(
                i ->
                    new FieldError(
                        "request", "field" + i, "value" + i, false, null, null, "错误 " + i))
            .collect(java.util.stream.Collectors.toList());

    when(bindingResult.getAllErrors()).thenReturn(errors);
    when(bindingResult.getErrorCount()).thenReturn(150);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证仅返回前 100 个错误
    assertThat(result).hasSize(100);
  }

  @Test
  @DisplayName("应该处理 null 字段名")
  void shouldHandleNullFieldName() {
    // Given: 准备包含 null 字段名的错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("request", "email", "test@example.com", false, null, null, "错误消息");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证处理成功
    assertThat(result).hasSize(1);
    assertThat(result.get(0).rejectedValue()).isEqualTo("test@example.com");
  }

  @Test
  @DisplayName("应该处理 null rejected value")
  void shouldHandleNullRejectedValue() {
    // Given: 准备包含 null rejected value 的错误
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError =
        new FieldError("request", "password", null, false, null, null, "密码不能为空");

    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
    when(bindingResult.getErrorCount()).thenReturn(1);

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证处理成功
    assertThat(result).hasSize(1);
    assertThat(result.get(0).rejectedValue()).isNull();
  }

  @Test
  @DisplayName("应该掩码所有配置的敏感模式")
  void shouldMaskAllConfiguredSensitivePatterns() {
    // Given: 准备包含多种敏感模式的错误
    BindingResult bindingResult = mock(BindingResult.class);
    List<ObjectError> errors =
        List.of(
            new FieldError("request", "password", "secret", false, null, null, "错误"),
            new FieldError("request", "apiToken", "token123", false, null, null, "错误"),
            new FieldError("request", "secretKey", "key456", false, null, null, "错误"),
            new FieldError("request", "credential", "cred789", false, null, null, "错误"),
            new FieldError("request", "authCode", "auth000", false, null, null, "错误"),
            new FieldError("request", "pinNumber", "1234", false, null, null, "错误"),
            new FieldError("request", "ssn", "123-45-6789", false, null, null, "错误"),
            new FieldError("request", "creditCard", "4111111111111111", false, null, null, "错误"),
            new FieldError("request", "accountNumber", "acc123", false, null, null, "错误"));

    when(bindingResult.getAllErrors()).thenReturn(errors);
    when(bindingResult.getErrorCount()).thenReturn(errors.size());

    // When: 格式化验证错误
    List<ValidationError> result = formatter.formatWithMasking(bindingResult);

    // Then: 验证所有敏感字段都被掩码
    assertThat(result).hasSize(9);
    assertThat(result).allMatch(error -> "***".equals(error.rejectedValue()));
  }
}
