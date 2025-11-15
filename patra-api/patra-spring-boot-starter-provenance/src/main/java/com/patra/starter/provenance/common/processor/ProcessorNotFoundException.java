package com.patra.starter.provenance.common.processor;

/**
 * Processor未找到异常
 *
 * <p>当尝试获取不存在的DataProcessor时抛出此异常。
 *
 * <p><strong>使用场景</strong>：
 *
 * <ul>
 *   <li>ProcessorRegistry.getProcessor()找不到对应的Processor
 *   <li>ProvenanceDataProvider查找Processor失败
 * </ul>
 *
 * <p><strong>设计理念</strong>：
 *
 * <ul>
 *   <li>继承RuntimeException，避免强制try-catch
 *   <li>提供详细的错误信息（包含DataType）
 *   <li>支持异常链（可选的cause参数）
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public class ProcessorNotFoundException extends RuntimeException {

  /**
   * 构造函数
   *
   * @param message 错误消息
   */
  public ProcessorNotFoundException(String message) {
    super(message);
  }

  /**
   * 构造函数（带原因）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public ProcessorNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
