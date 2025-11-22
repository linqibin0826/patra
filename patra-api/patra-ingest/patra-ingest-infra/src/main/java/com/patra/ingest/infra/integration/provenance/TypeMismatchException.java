package com.patra.ingest.infra.integration.provenance;

/// 类型不匹配异常
/// 
/// 当DataType.getDataClass()与TypeReference.getRawType()不一致时抛出此异常。
/// 
/// **使用场景**：
/// 
/// - ProvenanceDataAdapter验证类型一致性失败
///   - 调用者传入的DataType与TypeReference不匹配
/// 
/// **示例**：
/// 
/// ```java
/// // 错误用法：DataType.JOURNAL 期望 Journal，但传入 CanonicalPublication
/// TypeReference<CanonicalPublication> ref = new TypeReference<>() {;
/// adapter.fetchData(context, DataType.JOURNAL, ref, batch);
/// // 抛出 TypeMismatchException
/// ```
/// 
/// @author Patra Architecture Team
/// @since 0.1.0
public class TypeMismatchException extends RuntimeException {

  /// 使用错误消息构造异常
/// 
/// @param message 错误消息
  public TypeMismatchException(String message) {
    super(message);
  }

  /// 使用错误消息和原因构造异常
/// 
/// @param message 错误消息
/// @param cause 原因
  public TypeMismatchException(String message, Throwable cause) {
    super(message, cause);
  }
}
