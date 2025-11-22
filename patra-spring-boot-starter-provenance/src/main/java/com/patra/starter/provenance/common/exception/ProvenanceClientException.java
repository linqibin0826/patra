package com.patra.starter.provenance.common.exception;

import java.util.Objects;

/// Provenance 客户端异常基类
/// 
/// 封装通过 HTTP 直接调用 provenance 数据源时发生的下游错误。捕获额外的元数据 (HTTP 状态码、trace/correlation ID、响应体)以辅助故障排查。
/// 
/// **包含的诊断信息:**
/// 
/// - provenanceCode - 数据源标识符
///   - apiName - 失败的API方法名
///   - statusCode - HTTP 状态码(可选)
///   - traceId - 跟踪标识符(可选)
///   - responseBody - 原始响应体(可选)
/// 
/// @author linqibin
/// @since 0.1.0
public class ProvenanceClientException extends RuntimeException {

  private final String provenanceCode;
  private final String apiName;
  private final Integer statusCode;
  private final String traceId;
  private final String responseBody;

  /// 创建包含最少诊断信息的异常
/// 
/// @param provenanceCode provenance 数据源标识符
/// @param apiName 失败的 API 方法名
/// @param message 人类可读的消息
  public ProvenanceClientException(String provenanceCode, String apiName, String message) {
    this(provenanceCode, apiName, null, null, null, message, null);
  }

  /// 创建包含嵌套原因和消息的异常
/// 
/// @param provenanceCode provenance 数据源标识符
/// @param apiName 失败的 API 方法名
/// @param message 人类可读的消息
/// @param cause 底层异常
  public ProvenanceClientException(
      String provenanceCode, String apiName, String message, Throwable cause) {
    this(provenanceCode, apiName, null, null, null, message, cause);
  }

  /// 创建包含 HTTP 元数据和可选跟踪标识符的丰富异常
/// 
/// @param provenanceCode provenance 数据源标识符
/// @param apiName 失败的 API 方法名
/// @param statusCode 可选的下游 HTTP 状态码
/// @param traceId 可选的网关跟踪标识符
/// @param responseBody 可选的原始响应体(用于诊断)
/// @param message 人类可读的消息
/// @param cause 底层异常(如有)
  public ProvenanceClientException(
      String provenanceCode,
      String apiName,
      Integer statusCode,
      String traceId,
      String responseBody,
      String message,
      Throwable cause) {
    super(formatMessage(provenanceCode, apiName, statusCode, traceId, message), cause);
    this.provenanceCode = Objects.requireNonNull(provenanceCode, "provenanceCode cannot be null");
    this.apiName = Objects.requireNonNull(apiName, "apiName cannot be null");
    this.statusCode = statusCode;
    this.traceId = traceId;
    this.responseBody = responseBody;
  }

  private static String formatMessage(
      String provenanceCode, String apiName, Integer statusCode, String traceId, String message) {
    StringBuilder builder =
        new StringBuilder("[").append(provenanceCode).append("][").append(apiName).append("] ");
    if (statusCode != null) {
      builder.append("status=").append(statusCode).append(' ');
    }
    if (traceId != null) {
      builder.append("traceId=").append(traceId).append(' ');
    }
    builder.append(message);
    return builder.toString();
  }

  /// 获取 provenance 数据源标识符
/// 
/// @return provenance 代码字符串
  public String getProvenanceCode() {
    return provenanceCode;
  }

  /// 获取与失败关联的 API 名称
/// 
/// @return API 名称字符串
  public String getApiName() {
    return apiName;
  }

  /// 获取下游 HTTP 状态码(如已提供)
/// 
/// @return 可选的状态码
  public Integer getStatusCode() {
    return statusCode;
  }

  /// 获取由下游传播或调用方生成的跟踪标识符
/// 
/// @return 跟踪标识符,或 `null`
  public String getTraceId() {
    return traceId;
  }

  /// 获取从下游响应捕获的原始响应体
/// 
/// @return 响应载荷,或 `null`
  public String getResponseBody() {
    return responseBody;
  }
}
