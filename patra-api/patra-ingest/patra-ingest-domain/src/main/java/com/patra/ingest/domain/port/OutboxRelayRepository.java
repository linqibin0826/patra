package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.time.Instant;
import java.util.List;

/**
 * Outbox 转发存储端口(六边形架构 - Domain → Infrastructure)。
 *
 * <p><b>职责</b>: Outbox 转发器使用的持久化端口,负责:
 *
 * <ul>
 *   <li>获取待发布的消息并驱动状态转换
 *   <li>获取租约并记录发布结果
 *   <li>调度重试
 * </ul>
 *
 * <p><b>端口语义</b>: 此接口是六边形架构中的 <b>仓储端口(Repository Port)</b>,定义在 Domain
 * 层,由基础设施层(Infrastructure)实现,抽象存储交互细节。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxRelayRepository {

  /**
   * 获取待处理的 Outbox 消息。
   *
   * <p><b>支持可选的 channel 过滤</b>:
   *
   * <ul>
   *   <li>如果 {@code channel} 非 null,限制为该 channel
   *   <li>如果 {@code channel} 为 null,获取所有 channel 的消息
   * </ul>
   *
   * @param channel channel 标识符或 {@code null}(所有 channel)
   * @param availableTime 参考时间,用于确定发布资格
   * @param limit 检索的最大消息数量
   * @return 待处理消息列表(按实现排序);无符合条件时为空
   */
  List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit);

  /**
   * 为指定消息获取租约并标记为进行中。
   *
   * @param id Outbox 标识符
   * @param expectedVersion 乐观锁版本(null 跳过检查)
   * @param leaseOwner 租约所有者标记(通常包含调度器上下文)
   * @param leaseExpireAt 租约过期时间,过期后释放消息给其他实例
   * @return {@code true} 表示获取成功;{@code false} 表示失败
   */
  boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt);

  /**
   * 标记消息为已发布。
   *
   * @param id Outbox 标识符
   * @param expectedVersion 乐观锁版本
   */
  void markPublished(Long id, Long expectedVersion);

  /**
   * 将消息重新排队以进行重试(可恢复的失败后)。
   *
   * @param id Outbox 标识符
   * @param expectedVersion 乐观锁版本
   * @param retryCount 已执行的尝试次数
   * @param nextRetryAt 下次允许重试的时间
   * @param errorCode 错误分类(用于指标)
   * @param errorMessage 错误描述
   */
  void markDeferred(
      Long id,
      Long expectedVersion,
      int retryCount,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage);

  /**
   * 在重试耗尽后标记消息为死信。
   *
   * @param id Outbox 标识符
   * @param expectedVersion 乐观锁版本
   * @param retryCount 已执行的尝试次数
   * @param errorCode 错误分类
   * @param errorMessage 错误描述
   */
  void markFailed(
      Long id, Long expectedVersion, int retryCount, String errorCode, String errorMessage);
}
