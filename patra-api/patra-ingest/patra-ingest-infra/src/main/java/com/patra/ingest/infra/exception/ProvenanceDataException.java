package com.patra.ingest.infra.exception;

/// 数据源异常
/// 
/// 当Infrastructure层调用Framework层的ProvenanceDataProvider失败时抛出此异常。
/// 
/// 异常转换链:
/// 
/// - Framework层: ProvenanceClientException
///   - Infrastructure层: ProvenanceDataException (本类)
///   - Domain层: 无异常定义,由上层决定如何处理
/// 
/// @author Patra Architecture Team
/// @since 0.2.0
public class ProvenanceDataException extends RuntimeException {

  public ProvenanceDataException(String message) {
    super(message);
  }

  public ProvenanceDataException(String message, Throwable cause) {
    super(message, cause);
  }
}
