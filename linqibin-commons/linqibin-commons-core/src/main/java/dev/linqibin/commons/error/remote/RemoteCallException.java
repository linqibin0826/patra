package dev.linqibin.commons.error.remote;

import dev.linqibin.commons.error.problem.ErrorKeys;
import dev.linqibin.commons.error.trait.ErrorTrait;
import dev.linqibin.commons.error.trait.HasErrorTraits;
import dev.linqibin.commons.error.trait.StandardErrorTrait;
import java.util.Collections;
import java.util.Locale;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 远程服务调用失败时抛出的异常
///
/// 仅用于适配器层代码；应用层和领域层应将其转换为特定上下文的失败。
/// 该异常暴露下游元数据，如业务错误代码、HTTP 状态、跟踪标识符和扩展属性。
///
/// 实现 {@link HasErrorTraits} 接口以支持错误语义在服务间自动传播。当下游服务在响应中
/// 包含 `traits` 字段时，这些语义特征会被解析并通过此接口暴露，使上游服务的错误处理引擎
/// 能够自动识别错误类型。
///
/// **使用示例：**
/// ```java
/// try {
///     return provenanceEndpoint.getProvenance(code);
/// } catch (RemoteCallException ex) {
///     if (RemoteErrorHelper.isNotFound(ex)) {
///         throw new ProvenanceNotFoundException(code);
///     }
///     throw ex;
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
public class RemoteCallException extends RuntimeException implements HasErrorTraits {

  private static final Logger log = LoggerFactory.getLogger(RemoteCallException.class);

  /// 下游服务返回的业务错误代码（可能为 `null`）
  private final String errorCode;

  /// 下游响应的 HTTP 状态码
  private final int httpStatus;

  /// 触发调用的方法标识（如 "GET /api/users"）
  private final String methodKey;

  /// 从下游请求头或载荷中提取的跟踪标识符（可能为 `null`）
  private final String traceId;

  /// 下游服务返回的额外扩展属性
  private final Map<String, Object> extensions;

  /// 从下游响应解析的错误语义特征集合
  ///
  /// 用于服务间错误传播，使上游服务能够识别下游错误的语义类型（如 NOT_FOUND、CONFLICT 等）
  private final Set<ErrorTrait> errorTraits;

  /// 为非结构化错误响应构建异常
  ///
  /// @param httpStatus 下游服务返回的 HTTP 状态码
  /// @param message 原因短语或合成的错误消息
  /// @param methodKey 方法标识
  /// @param traceId 从响应头提取的跟踪标识符（如果有）
  public RemoteCallException(int httpStatus, String message, String methodKey, String traceId) {
    super(message);
    this.httpStatus = httpStatus;
    this.methodKey = methodKey;
    this.traceId = traceId;
    this.errorCode = null;
    this.extensions = Map.of();
    this.errorTraits = Set.of();
  }

  /// 使用所有字段的显式值构造异常
  ///
  /// @param errorCode 业务错误代码（可选）
  /// @param httpStatus HTTP 状态码
  /// @param message 错误消息
  /// @param methodKey 方法标识
  /// @param traceId 跟踪标识符（可选）
  /// @param extensions 扩展属性（可为 null）
  public RemoteCallException(
      String errorCode,
      int httpStatus,
      String message,
      String methodKey,
      String traceId,
      Map<String, Object> extensions) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.methodKey = methodKey;
    this.traceId = traceId;
    this.extensions = extensions != null ? new HashMap<>(extensions) : Map.of();
    this.errorTraits =
        parseErrorTraits(extensions != null ? extensions.get(ErrorKeys.TRAITS) : null);
  }

  /// 使用所有字段（包括显式指定的 errorTraits）构造异常
  ///
  /// @param errorCode 业务错误代码（可选）
  /// @param httpStatus HTTP 状态码
  /// @param message 错误消息
  /// @param methodKey 方法标识
  /// @param traceId 跟踪标识符（可选）
  /// @param extensions 扩展属性（可为 null）
  /// @param errorTraits 错误语义特征集合（可为 null）
  public RemoteCallException(
      String errorCode,
      int httpStatus,
      String message,
      String methodKey,
      String traceId,
      Map<String, Object> extensions,
      Set<ErrorTrait> errorTraits) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.methodKey = methodKey;
    this.traceId = traceId;
    this.extensions = extensions != null ? new HashMap<>(extensions) : Map.of();
    this.errorTraits = errorTraits != null ? Set.copyOf(errorTraits) : Set.of();
  }

  /// 获取下游服务返回的业务错误代码
  ///
  /// @return 业务错误代码，可能为 null
  public String getErrorCode() {
    return errorCode;
  }

  /// 获取下游响应的 HTTP 状态码
  ///
  /// @return HTTP 状态码
  public int getHttpStatus() {
    return httpStatus;
  }

  /// 获取方法标识
  ///
  /// @return 方法标识字符串
  public String getMethodKey() {
    return methodKey;
  }

  /// 获取跟踪标识符
  ///
  /// @return 跟踪标识符，可能为 null
  public String getTraceId() {
    return traceId;
  }

  /// 获取扩展属性映射
  ///
  /// @return 不可变的扩展属性映射
  public Map<String, Object> getExtensions() {
    return Collections.unmodifiableMap(extensions);
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return errorTraits;
  }

  /// 判断是否存在非空的业务错误代码
  ///
  /// @return 如果存在则返回 `true`
  public boolean hasErrorCode() {
    return errorCode != null && !errorCode.trim().isEmpty();
  }

  /// 判断跟踪标识符是否可用
  ///
  /// @return 如果可用则返回 `true`
  public boolean hasTraceId() {
    return traceId != null && !traceId.trim().isEmpty();
  }

  /// 检索扩展值
  ///
  /// @param key 扩展键
  /// @return 扩展值，如果不存在则返回 `null`
  public Object getExtension(String key) {
    return extensions.get(key);
  }

  /// 检索类型化的扩展值
  ///
  /// @param key 扩展键
  /// @param type 所需的值类型
  /// @param <T> 类型参数
  /// @return 转换后的值，如果不存在或类型不匹配则返回 `null`
  @SuppressWarnings("unchecked")
  public <T> T getExtension(String key, Class<T> type) {
    Object value = extensions.get(key);
    if (value != null && type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }

  /// 判断是否存在错误语义特征
  ///
  /// @return 如果存在则返回 `true`
  public boolean hasErrorTraits() {
    return errorTraits != null && !errorTraits.isEmpty();
  }

  /// 从扩展中解析错误语义特征
  ///
  /// 支持解析 {@link StandardErrorTrait} 中定义的标准特征。未知的特征名称会被忽略并记录日志。
  /// 此方法为 public 以供 {@code ProblemDetailErrorHandler} 等外部类复用。
  ///
  /// @param traitsValue 扩展中的 traits 字段值（预期为 List<String>）
  /// @return 解析后的错误特征集合，如果解析失败则返回空集合
  public static Set<ErrorTrait> parseErrorTraits(Object traitsValue) {
    if (traitsValue == null) {
      return Set.of();
    }

    if (traitsValue instanceof List<?> traitsList) {
      Set<ErrorTrait> traits = new HashSet<>();
      for (Object item : traitsList) {
        if (item instanceof String traitName) {
          ErrorTrait trait = parseTraitName(traitName);
          if (trait != null) {
            traits.add(trait);
          }
        }
      }
      if (!traits.isEmpty()) {
        log.debug("从下游响应解析到错误特征: {}", traits);
        return Collections.unmodifiableSet(traits);
      }
    }

    return Set.of();
  }

  /// 将特征名称解析为 ErrorTrait 枚举值
  ///
  /// @param name 特征名称
  /// @return 对应的 ErrorTrait，如果名称无效则返回 null
  private static ErrorTrait parseTraitName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    try {
      return StandardErrorTrait.valueOf(name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      log.debug("忽略未知的错误特征: {}", name);
      return null;
    }
  }
}
