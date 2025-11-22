package com.patra.ingest.domain.model.enums;

/// 中继执行结果枚举,表示 Outbox 消息中继尝试的结果。
///
/// 状态转换及含义:
///
/// - **PUBLISHED**: 消息成功发布到下游 broker(终态)
///   - **DEFERRED**: 中继因可重试错误失败,稍后将重试(瞬态)
///   - **FAILED**: 中继永久失败(达到最大重试次数或致命错误)
///   - **LEASE_MISSED**: 由于并发竞争未能获取租约(瞬态)
///
/// @author Patra Team
/// @since 0.1.0
public enum RelayStatus {

  /// 已发布;成功发布到下游 broker(终态)。
  PUBLISHED("PUBLISHED", "发布成功", true, false),

  /// 延迟重试;因瞬态错误失败,将使用退避策略重试。
  DEFERRED("DEFERRED", "延迟重试", false, true),

  /// 永久失败;达到最大重试次数或致命错误(终态)。
  FAILED("FAILED", "永久失败", true, false),

  /// 租约竞争失败;由于并发竞争未能获取租约(乐观锁失败)。
  LEASE_MISSED("LEASE_MISSED", "租约竞争失败", false, true);

  private final String code;
  private final String description;
  private final boolean terminal;
  private final boolean retryable;

  RelayStatus(String code, String description, boolean terminal, boolean retryable) {
    this.code = code;
    this.description = description;
    this.terminal = terminal;
    this.retryable = retryable;
  }

  /// 获取状态代码(与数据库枚举值匹配)。
  ///
  /// @return 状态代码字符串
  public String getCode() {
    return code;
  }

  /// 获取人类可读的描述(中文)。
  ///
  /// @return 描述字符串
  public String getDescription() {
    return description;
  }

  /// 检查是否为终态(无需进一步处理)。
  ///
  /// 终态: PUBLISHED, FAILED
  ///
  /// @return 如果此状态表示最终状态,则返回 true
  public boolean isTerminal() {
    return terminal;
  }

  /// 检查此状态是否表示可重试的失败。
  ///
  /// 可重试状态: DEFERRED, LEASE_MISSED
  ///
  /// @return 如果此状态允许重试,则返回 true
  public boolean isRetryable() {
    return retryable;
  }

  /// 将状态代码字符串解析为 RelayStatus 枚举。
  ///
  /// @param code 状态代码字符串(例如,"PUBLISHED")
  /// @return 对应的 RelayStatus 枚举
  /// @throws IllegalArgumentException 如果代码无效
  public static RelayStatus fromCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("RelayStatus 代码不能为 null 或空白");
    }
    for (RelayStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("无效的 RelayStatus 代码: " + code);
  }
}
