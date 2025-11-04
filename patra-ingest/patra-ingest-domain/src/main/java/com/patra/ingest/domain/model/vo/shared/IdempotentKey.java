package com.patra.ingest.domain.model.vo.shared;

/**
 * 幂等键 Value Object。
 *
 * <p>用于对计划、任务和其他领域对象进行去重,保证业务操作的幂等性。
 *
 * <p><b>格式:</b> 64 位 SHA-256 十六进制字符串
 *
 * <p><b>业务语义:</b>
 *
 * <ul>
 *   <li>相同的业务输入生成相同的幂等键
 *   <li>用于检测和防止重复的计划、任务创建
 *   <li>支持断点续传和重试场景
 * </ul>
 *
 * <p><b>不变性约束:</b> 长度必须为 64(创建时验证)。
 *
 * @param value 64 位 SHA-256 十六进制字符串
 * @author Patra Team
 * @since 0.1.0
 */
public record IdempotentKey(String value) {
  public IdempotentKey {
    if (value == null || value.length() != 64) {
      throw new IllegalArgumentException("幂等键必须是 64 位 SHA256 十六进制字符串");
    }
  }
}
