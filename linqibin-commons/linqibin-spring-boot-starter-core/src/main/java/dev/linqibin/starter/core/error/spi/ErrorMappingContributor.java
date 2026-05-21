package dev.linqibin.starter.core.error.spi;

import dev.linqibin.commons.error.codes.ErrorCodeLike;
import java.util.Optional;
import org.springframework.core.Ordered;

/// SPI 接口,用于提供细粒度的错误码映射。
///
/// 允许服务覆盖特定异常的默认解析逻辑,实现自定义的异常到错误码映射。
///
/// **优先级控制（方案 4）**:
///
/// 实现 {@link Ordered} 接口以控制 Contributors 的执行顺序:
///
/// - 数值越小,优先级越高（先执行）
/// - 默认优先级为 0
/// - 推荐: 高频异常的 Contributor 使用较高优先级（负数）
///
/// **使用示例**:
///
/// ```java
/// @Component
/// @Order(Ordered.HIGHEST_PRECEDENCE)  // 最高优先级
/// public class DataLayerErrorMappingContributor implements ErrorMappingContributor {
///   @Override
///   public Optional<ErrorCodeLike> mapException(Throwable exception) {
///     if (exception instanceof SQLException) {
///       return Optional.of(http.INTERNAL_ERROR());
///     }
///     return Optional.empty();  // 传递给下一个贡献者
///   }
/// }
///
/// @Component
/// @Order(100)  // 较低优先级
/// public class CustomBusinessErrorContributor implements ErrorMappingContributor {
///   @Override
///   public Optional<ErrorCodeLike> mapException(Throwable exception) {
///     if (exception instanceof MyCustomException) {
///       return Optional.of(new SimpleErrorCode("CUSTOM", "0422"));
///     }
///     return Optional.empty();
///   }
/// }
/// ```
///
/// **最佳实践**:
///
/// - 高频异常（如 SQL 异常）应使用高优先级
/// - 业务特定异常可使用较低优先级
/// - 不要在 Contributor 中抛出异常（会被 Engine 捕获并跳过）
///
/// @author linqibin
/// @since 0.1.0
public interface ErrorMappingContributor extends Ordered {

  /// 为提供的异常提供错误码(如果此贡献者可以处理)。
  ///
  /// **实现要求**:
  ///
  /// - 快速失败: 如果不能处理该异常,立即返回 {@code Optional.empty()}
  /// - 无副作用: 不应抛出异常或修改状态
  /// - 线程安全: 支持并发调用
  ///
  /// @param exception 要映射的异常(永不为 `null`)
  /// @return 可选的错误码;如果贡献者不适用则返回空
  Optional<ErrorCodeLike> mapException(Throwable exception);

  /// 返回此 Contributor 的执行优先级。
  ///
  /// **优先级指南**:
  ///
  /// - {@link Ordered#HIGHEST_PRECEDENCE}: 最高优先级（如数据层异常）
  /// - {@code -100 ~ 0}: 高优先级（基础设施异常）
  /// - {@code 0 ~ 100}: 中优先级（业务异常）
  /// - {@code 100+}: 低优先级（兜底逻辑）
  ///
  /// @return 优先级数值,默认为 0
  @Override
  default int getOrder() {
    return 0;
  }
}
