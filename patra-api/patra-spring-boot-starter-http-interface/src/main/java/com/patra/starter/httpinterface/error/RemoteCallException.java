package com.patra.starter.httpinterface.error;

import com.patra.common.error.problem.ErrorKeys;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.common.error.trait.StandardErrorTrait;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;

/// HTTP Interface 客户端从下游服务接收到错误响应时抛出的异常
///
/// 仅用于适配器层代码；应用层和领域层应将其转换为特定上下文的失败。
/// 该异常暴露下游元数据，如业务错误代码、HTTP 状态、跟踪标识符和 {@link ProblemDetail} 扩展映射。
///
/// 实现 {@link HasErrorTraits} 接口以支持错误语义在服务间自动传播。当下游服务在 ProblemDetail
/// 响应中包含 `traits` 字段时，这些语义特征会被解析并通过此接口暴露，使上游服务的
/// {@link com.patra.starter.core.error.engine.ErrorResolutionEngine} 能够自动识别错误类型。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Getter
public class RemoteCallException extends RuntimeException implements HasErrorTraits {

  /// 下游服务返回的业务错误代码（可能为 `null`）
  private final String errorCode;

  /// 下游响应的 HTTP 状态码
  private final int httpStatus;

  /// 触发调用的方法标识（如 "GET /api/users"）
  private final String methodKey;

  /// 从下游请求头或载荷中提取的跟踪标识符（可能为 `null`）
  private final String traceId;

  /// 下游服务返回的额外 ProblemDetail 扩展属性
  private final Map<String, Object> extensions;

  /// 从下游 ProblemDetail 解析的错误语义特征集合
  ///
  /// 用于服务间错误传播，使上游服务能够识别下游错误的语义类型（如 NOT_FOUND、CONFLICT 等）
  private final Set<ErrorTrait> errorTraits;

  /// 从下游 {@link ProblemDetail} 构建异常，提取错误代码、跟踪标识符、扩展属性和错误特征
  ///
  /// @param problemDetail 下游服务返回的 ProblemDetail
  /// @param methodKey 与调用关联的方法标识
  public RemoteCallException(ProblemDetail problemDetail, String methodKey) {
    super(problemDetail.getDetail());
    this.httpStatus = problemDetail.getStatus();
    this.methodKey = methodKey;

    // 从扩展映射中提取错误代码和跟踪信息
    Map<String, Object> properties = problemDetail.getProperties();
    if (properties == null) {
      properties = Collections.emptyMap();
    }
    this.errorCode = (String) properties.get(ErrorKeys.CODE);
    this.traceId = (String) properties.get(ErrorKeys.TRACE_ID);

    // 复制所有扩展字段以供后续检查
    this.extensions = new HashMap<>(properties);

    // 解析错误语义特征
    this.errorTraits = parseErrorTraits(properties.get(ErrorKeys.TRAITS));
  }

  /// 为非 ProblemDetail 响应构建异常（严格模式回退或容错模式场景）
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
    this.extensions = Collections.emptyMap();
    this.errorTraits = Collections.emptySet();
  }

  /// 使用所有字段的显式值构造异常
  ///
  /// @param errorCode 业务错误代码（可选）
  /// @param httpStatus HTTP 状态码
  /// @param message 错误消息
  /// @param methodKey 方法标识
  /// @param traceId 跟踪标识符（可选）
  /// @param extensions ProblemDetail 扩展（可为 null）
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
    this.extensions = extensions != null ? new HashMap<>(extensions) : Collections.emptyMap();
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
  /// @param extensions ProblemDetail 扩展（可为 null）
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
    this.extensions = extensions != null ? new HashMap<>(extensions) : Collections.emptyMap();
    this.errorTraits = errorTraits != null ? Set.copyOf(errorTraits) : Collections.emptySet();
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

  /// 检索 ProblemDetail 扩展值
  ///
  /// @param key 扩展键
  /// @return 扩展值，如果不存在则返回 `null`
  public Object getExtension(String key) {
    return extensions.get(key);
  }

  /// 检索类型化的 ProblemDetail 扩展值
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

  /// 获取 ProblemDetail 扩展的不可变副本
  ///
  /// @return 不可变的扩展映射
  public Map<String, Object> getAllExtensions() {
    return Collections.unmodifiableMap(extensions);
  }

  /// 判断是否存在错误语义特征
  ///
  /// @return 如果存在则返回 `true`
  public boolean hasErrorTraits() {
    return errorTraits != null && !errorTraits.isEmpty();
  }

  /// 从 ProblemDetail 扩展中解析错误语义特征
  ///
  /// 支持解析 {@link StandardErrorTrait} 中定义的标准特征。未知的特征名称会被忽略并记录日志。
  ///
  /// @param traitsValue ProblemDetail 中的 traits 字段值（预期为 List<String>）
  /// @return 解析后的错误特征集合，如果解析失败则返回空集合
  private static Set<ErrorTrait> parseErrorTraits(Object traitsValue) {
    if (traitsValue == null) {
      return Collections.emptySet();
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

    return Collections.emptySet();
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
      return StandardErrorTrait.valueOf(name.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.debug("忽略未知的错误特征: {}", name);
      return null;
    }
  }
}
