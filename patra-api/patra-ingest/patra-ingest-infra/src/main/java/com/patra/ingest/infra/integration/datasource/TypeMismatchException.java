package com.patra.ingest.infra.integration.datasource;

/**
 * 类型不匹配异常
 *
 * <p>当DataType.getDataClass()与TypeReference.getRawType()不一致时抛出此异常。
 *
 * <p><strong>使用场景</strong>：
 *
 * <ul>
 *   <li>ProvenanceDataAdapter验证类型一致性失败
 *   <li>调用者传入的DataType与TypeReference不匹配
 * </ul>
 *
 * <p><strong>示例</strong>：
 *
 * <pre>{@code
 * // 错误用法：DataType.JOURNAL 期望 Journal，但传入 CanonicalLiterature
 * TypeReference<CanonicalLiterature> ref = new TypeReference<>() {};
 * adapter.fetchData(context, DataType.JOURNAL, ref, batch);
 * // 抛出 TypeMismatchException
 * }</pre>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public class TypeMismatchException extends RuntimeException {

  /**
   * 使用错误消息构造异常
   *
   * @param message 错误消息
   */
  public TypeMismatchException(String message) {
    super(message);
  }

  /**
   * 使用错误消息和原因构造异常
   *
   * @param message 错误消息
   * @param cause 原因
   */
  public TypeMismatchException(String message, Throwable cause) {
    super(message, cause);
  }
}
