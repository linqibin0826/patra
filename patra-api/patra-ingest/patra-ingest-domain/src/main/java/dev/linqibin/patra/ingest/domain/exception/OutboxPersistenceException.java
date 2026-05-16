package dev.linqibin.patra.ingest.domain.exception;

import dev.linqibin.commons.error.trait.StandardErrorTrait;

/// Outbox 持久化异常。
///
/// 触发场景:在 Outbox 消息状态转换过程中持久化失败,具体包括:
///
/// - 从待发布 → 已发布状态转换失败
///   - 标记消息为重试状态失败(通常由并发写入冲突导致)
///   - 标记消息为死信状态失败
///
/// 典型根因:
///
/// - 并发更新冲突(乐观锁导致 update 影响 0 行)
///   - 数据库临时不可用(连接池耗尽、网络抖动)
///   - 序列号生成失败
///
/// 恢复策略:
///
/// - **并发冲突**:根据重试次数使用指数退避算法重试。
///   - **连接/超时**:归类为可重试错误,由调度器重新尝试。
///   - **持续失败(超过阈值)**:触发告警并检查数据库健康状态或表锁情况。
///
/// @author linqibin
/// @since 0.1.0
public class OutboxPersistenceException extends IngestException {

  public enum Stage {
    /// 标记消息为已发布状态时失败。
    MARK_PUBLISHED,
    /// 标记消息为重试状态时失败(通常由并发写入导致)。
    MARK_RETRY,
    /// 标记消息为死信状态时失败。
    MARK_DEAD,
    /// 批量插入消息时失败。
    BATCH_INSERT
  }

  /// 持久化失败所在的阶段。
  private final Stage stage;

  /// 构造 Outbox 持久化异常。
  ///
  /// @param stage 失败阶段
  /// @param message 描述性消息
  public OutboxPersistenceException(Stage stage, String message) {
    super(message, StandardErrorTrait.CONFLICT);
    this.stage = stage;
  }

  /// 构造 Outbox 持久化异常并附带底层原因。
  ///
  /// @param stage 失败阶段
  /// @param message 描述性消息
  /// @param cause 底层原因
  public OutboxPersistenceException(Stage stage, String message, Throwable cause) {
    super(message, cause, StandardErrorTrait.CONFLICT);
    this.stage = stage;
  }

  /// 获取持久化失败所在的阶段。
  ///
  /// @return 阶段枚举
  public Stage getStage() {
    return stage;
  }
}
