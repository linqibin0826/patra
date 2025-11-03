package com.patra.ingest.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.EnumSet;
import java.util.Set;

/**
 * Outbox 持久化异常。
 *
 * <p>触发场景:在 Outbox 消息状态转换过程中持久化失败,具体包括:
 *
 * <ul>
 *   <li>从待发布 → 已发布状态转换失败
 *   <li>标记消息为重试状态失败(通常由并发写入冲突导致)
 *   <li>标记消息为死信状态失败
 * </ul>
 *
 * <p>典型根因:
 *
 * <ul>
 *   <li>并发更新冲突(乐观锁导致 update 影响 0 行)
 *   <li>数据库临时不可用(连接池耗尽、网络抖动)
 *   <li>序列号生成失败
 * </ul>
 *
 * <p>恢复策略:
 *
 * <ul>
 *   <li><b>并发冲突</b>:根据重试次数使用指数退避算法重试。
 *   <li><b>连接/超时</b>:归类为可重试错误,由调度器重新尝试。
 *   <li><b>持续失败(超过阈值)</b>:触发告警并检查数据库健康状态或表锁情况。
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class OutboxPersistenceException extends IngestException implements HasErrorTraits {

  public enum Stage {
    /** 标记消息为已发布状态时失败。 */
    MARK_PUBLISHED,
    /** 标记消息为重试状态时失败(通常由并发写入导致)。 */
    MARK_RETRY,
    /** 标记消息为死信状态时失败。 */
    MARK_DEAD
  }

  /** 持久化失败所在的阶段。 */
  private final Stage stage;

  /**
   * 构造 Outbox 持久化异常。
   *
   * @param stage 失败阶段
   * @param message 描述性消息
   */
  public OutboxPersistenceException(Stage stage, String message) {
    super(message);
    this.stage = stage;
  }

  /**
   * 构造 Outbox 持久化异常并附带底层原因。
   *
   * @param stage 失败阶段
   * @param message 描述性消息
   * @param cause 底层原因
   */
  public OutboxPersistenceException(Stage stage, String message, Throwable cause) {
    super(message, cause);
    this.stage = stage;
  }

  /**
   * 获取持久化失败所在的阶段。
   *
   * @return 阶段枚举
   */
  public Stage getStage() {
    return stage;
  }

  @Override
  public Set<ErrorTrait> getErrorTraits() {
    return EnumSet.of(ErrorTrait.CONFLICT);
  }
}
