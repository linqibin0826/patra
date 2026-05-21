package dev.linqibin.commons.error;

import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import java.util.Set;

/// 领域层异常的基类型。
///
/// 为领域特定故障提供框架无关的抽象,使领域层与 Spring 和其他基础设施关注点解耦。
///
/// **核心设计原则**:
///
/// - **框架无关**: 不依赖任何基础设施技术（HTTP、Spring 等）
/// - **强制语义化**: 所有领域异常必须携带至少一个 {@link ErrorTrait}，表达明确的业务语义
/// - **编译时安全**: 通过构造函数约束确保异常的语义完整性
/// - **可扩展**: 支持标准 trait 和业务自定义 trait
///
/// **使用示例**:
///
/// ```java
/// // 使用标准 trait
/// public class UserNotFoundException extends DomainException {
///     public UserNotFoundException(String userId) {
///         super("用户未找到: " + userId, StandardErrorTrait.NOT_FOUND);
///     }
/// }
///
/// // 使用多个 trait（表达更丰富的语义）
/// public class ResourceConflictException extends DomainException {
///     public ResourceConflictException(String resourceId) {
///         super(
///             "资源冲突: " + resourceId,
///             StandardErrorTrait.CONFLICT,
///             StandardErrorTrait.RULE_VIOLATION
///         );
///     }
/// }
///
/// // 包装底层异常
/// public class ExternalServiceTimeoutException extends DomainException {
///     public ExternalServiceTimeoutException(String service, Throwable cause) {
///         super(
///             "外部服务超时: " + service,
///             cause,
///             StandardErrorTrait.TIMEOUT,
///             StandardErrorTrait.DEP_UNAVAILABLE
///         );
///     }
/// }
/// ```
///
/// **设计决策**:
///
/// - ✅ 强制提供 trait - 确保所有异常都有明确的业务语义
/// - ✅ varargs 支持 - 允许一个异常携带多个语义特征
/// - ✅ 不可变 traits - 异常创建后其语义不可改变
/// - ❌ 无默认构造函数 - 避免无语义的异常被创建
///
/// @author linqibin
/// @since 0.1.0
/// @see ErrorTrait
/// @see StandardErrorTrait
/// @see HasErrorTraits
public abstract class DomainException extends RuntimeException implements HasErrorTraits {

  /// 与此异常关联的语义特征集合（不可变）。
  private final Set<ErrorTrait> traits;

  /// 使用提供的消息和语义特征创建领域异常。
  ///
  /// **核心构造函数** - 所有领域异常必须提供至少一个语义特征。
  ///
  /// @param message 异常消息（应包含足够的上下文信息用于调试）
  /// @param traits 语义特征（至少提供一个，推荐使用 {@link StandardErrorTrait}）
  /// @throws IllegalArgumentException 如果未提供任何 trait
  protected DomainException(String message, ErrorTrait... traits) {
    super(message);
    if (traits == null || traits.length == 0) {
      throw new IllegalArgumentException(
          "DomainException 必须提供至少一个 ErrorTrait。"
              + "这确保了异常的业务语义完整性。"
              + "请使用 StandardErrorTrait 或自定义 trait。");
    }
    this.traits = Set.of(traits);
  }

  /// 使用提供的消息、根本原因和语义特征创建领域异常。
  ///
  /// **包装异常构造函数** - 用于包装底层异常（如数据库异常、外部服务异常）。
  ///
  /// @param message 异常消息（应包含足够的上下文信息用于调试）
  /// @param cause 根本原因（底层异常）
  /// @param traits 语义特征（至少提供一个，推荐使用 {@link StandardErrorTrait}）
  /// @throws IllegalArgumentException 如果未提供任何 trait
  protected DomainException(String message, Throwable cause, ErrorTrait... traits) {
    super(message, cause);
    if (traits == null || traits.length == 0) {
      throw new IllegalArgumentException(
          "DomainException 必须提供至少一个 ErrorTrait。"
              + "这确保了异常的业务语义完整性。"
              + "请使用 StandardErrorTrait 或自定义 trait。");
    }
    this.traits = Set.of(traits);
  }

  /// 返回与此异常关联的语义特征。
  ///
  /// 这些特征被错误解析引擎用于映射到适当的 HTTP 状态码和错误响应。
  ///
  /// @return 不可变的特征集合（保证非空且至少包含一个元素）
  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return traits;
  }
}
