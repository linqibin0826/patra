package com.patra.ingest.testutil;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox 消息测试数据构建器。
 *
 * <p>提供流式 API 构建测试用的 {@link OutboxMessage} 实例,简化测试数据准备。
 *
 * <h3>设计原则</h3>
 *
 * <ul>
 *   <li><strong>合理默认值</strong>: 所有必填字段都有预设值,可直接构建
 *   <li><strong>流式 API</strong>: 支持链式调用,提高可读性
 *   <li><strong>场景预设</strong>: 提供常见测试场景的工厂方法
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 1. 使用默认值快速构建
 * OutboxMessage msg = OutboxMessageTestBuilder.aValidPendingMessage().build();
 *
 * // 2. 自定义部分字段
 * OutboxMessage msg = OutboxMessageTestBuilder.aValidPendingMessage()
 *     .channel(MessageChannels.PUBLICATION_READY)
 *     .aggregateId(123L)
 *     .build();
 *
 * // 3. 构建特定场景
 * OutboxMessage published = OutboxMessageTestBuilder.aPublishedMessage().build();
 * OutboxMessage failed = OutboxMessageTestBuilder.aFailedMessage().build();
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public final class OutboxMessageTestBuilder {

  private Long id;
  private Long version;
  private String aggregateType = "TASK";
  private Long aggregateId = 1001L;
  private String channel = "INGEST_TASK";
  private String opType = "CREATE";
  private String partitionKey = "partition-1";
  private String dedupKey = "dedup-" + UUID.randomUUID();
  private String payloadJson = "{\"taskId\":1001,\"status\":\"PENDING\"}";
  private String headersJson = null;
  private Instant notBefore = null;
  private String statusCode = "PENDING";
  private Integer retryCount = 0;
  private Instant nextRetryAt = null;
  private String errorCode = null;
  private String errorMsg = null;
  private String leaseOwner = null;
  private Instant leaseExpireAt = null;

  private OutboxMessageTestBuilder() {}

  /**
   * 创建一个有效的 PENDING 状态消息构建器 (推荐使用)。
   *
   * <p><strong>预设值</strong>:
   *
   * <ul>
   *   <li>aggregateType: TASK
   *   <li>channel: TASK_READY
   *   <li>statusCode: PENDING
   *   <li>retryCount: 0
   *   <li>dedupKey: 自动生成 UUID
   * </ul>
   *
   * @return 新的构建器实例
   */
  public static OutboxMessageTestBuilder aValidPendingMessage() {
    return new OutboxMessageTestBuilder();
  }

  /**
   * 创建一个已发布状态的消息构建器。
   *
   * <p>用于测试已完成发布的场景。
   *
   * @return 配置为 PUBLISHED 状态的构建器
   */
  public static OutboxMessageTestBuilder aPublishedMessage() {
    return new OutboxMessageTestBuilder().statusCode("PUBLISHED");
  }

  /**
   * 创建一个发布中状态的消息构建器。
   *
   * <p>用于测试正在发布中的场景 (已获取租约)。
   *
   * @return 配置为 PUBLISHING 状态的构建器
   */
  public static OutboxMessageTestBuilder aPublishingMessage() {
    return new OutboxMessageTestBuilder()
        .statusCode("PUBLISHING")
        .leaseOwner("relay-instance-1")
        .leaseExpireAt(Instant.now().plusSeconds(300));
  }

  /**
   * 创建一个发布失败状态的消息构建器。
   *
   * <p>用于测试失败重试场景。
   *
   * @return 配置为 FAILED 状态的构建器
   */
  public static OutboxMessageTestBuilder aFailedMessage() {
    return new OutboxMessageTestBuilder()
        .statusCode("FAILED")
        .retryCount(3)
        .errorCode("SEND_FAILED")
        .errorMsg("RocketMQ connection timeout");
  }

  /**
   * 创建一个带租约的消息构建器。
   *
   * <p>用于测试租约机制相关场景。
   *
   * @return 配置了租约信息的构建器
   */
  public static OutboxMessageTestBuilder aMessageWithLease() {
    return new OutboxMessageTestBuilder()
        .leaseOwner("relay-instance-1")
        .leaseExpireAt(Instant.now().plusSeconds(300));
  }

  /**
   * 创建一个出版物数据就绪消息构建器。
   *
   * <p>用于测试出版物数据采集完成场景。
   *
   * @return 配置为出版物就绪通道的构建器
   */
  public static OutboxMessageTestBuilder aPublicationReadyMessage() {
    return new OutboxMessageTestBuilder()
        .channel("INGEST_PUBLICATION")
        .aggregateType("PUBLICATION")
        .opType("INGEST")
        .payloadJson("{\"publicationId\":9001,\"source\":\"PUBMED\"}");
  }

  // ========== Fluent Setters ==========

  public OutboxMessageTestBuilder id(Long id) {
    this.id = id;
    return this;
  }

  public OutboxMessageTestBuilder version(Long version) {
    this.version = version;
    return this;
  }

  public OutboxMessageTestBuilder aggregateType(String aggregateType) {
    this.aggregateType = aggregateType;
    return this;
  }

  public OutboxMessageTestBuilder aggregateId(Long aggregateId) {
    this.aggregateId = aggregateId;
    return this;
  }

  public OutboxMessageTestBuilder channel(String channel) {
    this.channel = channel;
    return this;
  }

  public OutboxMessageTestBuilder opType(String opType) {
    this.opType = opType;
    return this;
  }

  public OutboxMessageTestBuilder partitionKey(String partitionKey) {
    this.partitionKey = partitionKey;
    return this;
  }

  public OutboxMessageTestBuilder dedupKey(String dedupKey) {
    this.dedupKey = dedupKey;
    return this;
  }

  public OutboxMessageTestBuilder payloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
    return this;
  }

  public OutboxMessageTestBuilder headersJson(String headersJson) {
    this.headersJson = headersJson;
    return this;
  }

  public OutboxMessageTestBuilder notBefore(Instant notBefore) {
    this.notBefore = notBefore;
    return this;
  }

  public OutboxMessageTestBuilder statusCode(String statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  public OutboxMessageTestBuilder retryCount(Integer retryCount) {
    this.retryCount = retryCount;
    return this;
  }

  public OutboxMessageTestBuilder nextRetryAt(Instant nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
    return this;
  }

  public OutboxMessageTestBuilder errorCode(String errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public OutboxMessageTestBuilder errorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
    return this;
  }

  public OutboxMessageTestBuilder leaseOwner(String leaseOwner) {
    this.leaseOwner = leaseOwner;
    return this;
  }

  public OutboxMessageTestBuilder leaseExpireAt(Instant leaseExpireAt) {
    this.leaseExpireAt = leaseExpireAt;
    return this;
  }

  /**
   * 构建 OutboxMessage 实例。
   *
   * @return 不可变的 OutboxMessage 实例
   */
  public OutboxMessage build() {
    return OutboxMessage.builder()
        .id(id)
        .version(version)
        .aggregateType(aggregateType)
        .aggregateId(aggregateId)
        .channel(channel)
        .opType(opType)
        .partitionKey(partitionKey)
        .dedupKey(dedupKey)
        .payloadJson(payloadJson)
        .headersJson(headersJson)
        .notBefore(notBefore)
        .statusCode(statusCode)
        .retryCount(retryCount)
        .nextRetryAt(nextRetryAt)
        .errorCode(errorCode)
        .errorMsg(errorMsg)
        .leaseOwner(leaseOwner)
        .leaseExpireAt(leaseExpireAt)
        .build();
  }
}
