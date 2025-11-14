package com.patra.starter.provenance.common.processor;

/**
 * 数据转换异常
 *
 * <p>当原始数据无法转换为目标类型时抛出此异常。
 *
 * <p><strong>使用场景</strong>：
 *
 * <ul>
 *   <li>原始数据格式不正确
 *   <li>必填字段缺失
 *   <li>数据类型不匹配
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public class TransformationException extends Exception {

  /**
   * 构造转换异常
   *
   * @param message 错误消息
   */
  public TransformationException(String message) {
    super(message);
  }

  /**
   * 构造转换异常（带原因）
   *
   * @param message 错误消息
   * @param cause 原因异常
   */
  public TransformationException(String message, Throwable cause) {
    super(message, cause);
  }
}
