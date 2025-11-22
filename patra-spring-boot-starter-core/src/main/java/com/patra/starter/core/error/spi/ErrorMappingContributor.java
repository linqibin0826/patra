package com.patra.starter.core.error.spi;

import com.patra.common.error.codes.ErrorCodeLike;
import java.util.Optional;

/// SPI 接口,用于提供细粒度的错误码映射。
///
/// 允许服务覆盖特定异常的默认解析逻辑,实现自定义的异常到错误码映射。
///
/// 使用示例:
///
/// ```java
/// @Component
/// public class MyErrorMappingContributor implements ErrorMappingContributor {
///   @Override
///   public Optional<ErrorCodeLike> mapException(Throwable exception) {
///     if (exception instanceof MyCustomException) {
///       return Optional.of(new SimpleErrorCode("CUSTOM", "0422"));
///     return Optional.empty();  // 传递给下一个贡献者
/// ```
///
/// @author Patra Team
/// @since 2.0
public interface ErrorMappingContributor {

  /// 为提供的异常提供错误码(如果此贡献者可以处理)。
  ///
  /// @param exception 要映射的异常(永不为 `null`)
  /// @return 可选的错误码;如果贡献者不适用则返回空
  Optional<ErrorCodeLike> mapException(Throwable exception);
}
