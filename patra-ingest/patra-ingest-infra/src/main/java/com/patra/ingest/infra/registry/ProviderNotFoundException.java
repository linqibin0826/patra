package com.patra.ingest.infra.registry;

/**
 * Provider未找到异常
 *
 * <p>当尝试获取不存在的Provider或不支持的数据类型时抛出此异常。
 *
 * <h2>使用场景：</h2>
 *
 * <ul>
 *   <li>指定的provenanceCode不存在
 *   <li>Provider不支持指定的DataType
 *   <li>(provenanceCode, dataType)组合未注册
 * </ul>
 *
 * @author Patra Architecture Team
 * @since 0.1.0
 */
public class ProviderNotFoundException extends RuntimeException {

  /**
   * 构造函数
   *
   * @param message 错误消息
   */
  public ProviderNotFoundException(String message) {
    super(message);
  }

  /**
   * 构造函数
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public ProviderNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
