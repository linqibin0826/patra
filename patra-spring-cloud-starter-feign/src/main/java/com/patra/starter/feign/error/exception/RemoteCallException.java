package com.patra.starter.feign.error.exception;

import com.patra.common.error.problem.ErrorKeys;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.ProblemDetail;

/// Feign 客户端从下游服务接收到错误响应时抛出的异常
/// 
/// 仅用于适配器层代码;应用层和领域层应将其转换为特定上下文的失败。 该异常暴露下游元数据,如业务错误代码、HTTP 状态、跟踪标识符和 {@link ProblemDetail} 扩展映射。
/// 
/// 由 {@link com.patra.starter.feign.error.decoder.ProblemDetailErrorDecoder} 构造, 通常通过 {@link
/// com.patra.starter.feign.error.util.RemoteErrorHelper} 检查。
@Getter
public class RemoteCallException extends RuntimeException {

  /// 下游服务返回的业务错误代码(可能为 `null`)
  private final String errorCode;

  /// 下游响应的 HTTP 状态码
  private final int httpStatus;

  /// 触发调用的 Feign 方法键
  private final String methodKey;

  /// 从下游请求头或载荷中提取的跟踪标识符(可能为 `null`)
  private final String traceId;

  /// 下游服务返回的额外 ProblemDetail 扩展属性
  private final Map<String, Object> extensions;

  /// 从下游 {@link ProblemDetail} 构建异常,提取错误代码、跟踪标识符和扩展属性
/// 
/// @param problemDetail 下游服务返回的 ProblemDetail
/// @param methodKey 与调用关联的 Feign 方法键
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
  }

  /// 为非 ProblemDetail 响应构建异常(严格模式回退或容错模式场景)
/// 
/// @param httpStatus 下游服务返回的 HTTP 状态码
/// @param message 原因短语或合成的错误消息
/// @param methodKey Feign 方法键
/// @param traceId 从响应头提取的跟踪标识符(如果有)
  public RemoteCallException(int httpStatus, String message, String methodKey, String traceId) {
    super(message);
    this.httpStatus = httpStatus;
    this.methodKey = methodKey;
    this.traceId = traceId;
    this.errorCode = null;
    this.extensions = Collections.emptyMap();
  }

  /// 使用所有字段的显式值构造异常
/// 
/// @param errorCode 业务错误代码(可选)
/// @param httpStatus HTTP 状态码
/// @param message 错误消息
/// @param methodKey Feign 方法键
/// @param traceId 跟踪标识符(可选)
/// @param extensions ProblemDetail 扩展(可为 null)
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
/// @return 扩展值,如果不存在则返回 `null`
  public Object getExtension(String key) {
    return extensions.get(key);
  }

  /// 检索类型化的 ProblemDetail 扩展值
/// 
/// @param key 扩展键
/// @param type 所需的值类型
/// @param <T> 类型参数
/// @return 转换后的值,如果不存在或类型不匹配则返回 `null`
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
}
