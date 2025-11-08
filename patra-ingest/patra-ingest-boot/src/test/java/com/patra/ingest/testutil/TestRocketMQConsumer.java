package com.patra.ingest.testutil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 测试用 RocketMQ 消息消费者。
 *
 * <p>使用 Spring 管理的 @RocketMQMessageListener 注解监听测试消息，收集消息用于断言验证。
 *
 * <h3>设计考虑</h3>
 *
 * <ul>
 *   <li><strong>Spring 管理生命周期</strong>: 避免手动创建 Consumer 的初始化延迟问题
 *   <li><strong>线程安全</strong>: 使用 ConcurrentHashMap 存储接收到的消息
 *   <li><strong>按需启用</strong>: 通过 @ConditionalOnProperty 控制是否启用（仅在集成测试中启用）
 *   <li><strong>Key 索引</strong>: 使用消息的 KEYS 作为唯一标识，方便测试断言
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <pre>{@code
 * @Autowired
 * private TestRocketMQConsumer testConsumer;
 *
 * // 清空之前的消息
 * testConsumer.clear();
 *
 * // 发送消息
 * publisher.publish(outboxMessage, null);
 *
 * // 等待消息被接收
 * await().atMost(5, SECONDS)
 *     .untilAsserted(() ->
 *         assertThat(testConsumer.hasMessage("test-key")).isTrue()
 *     );
 *
 * // 验证消息内容
 * MessageExt msg = testConsumer.getMessage("test-key");
 * assertThat(new String(msg.getBody())).contains("expected content");
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 * @see RocketMqOutboxPublisherIT
 * @see TestMessageCollector
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "patra.ingest.test.rocketmq.consumer.enabled",
    havingValue = "true",
    matchIfMissing = false // 默认禁用（仅在集成测试中启用）
)
@RocketMQMessageListener(
    topic = "${patra.ingest.mq.topics.task-ready:INGEST_TASK_READY}",
    consumerGroup = "test-consumer-group-integration",
    selectorExpression = "*" // 接收所有 TAGS
)
public class TestRocketMQConsumer implements RocketMQListener<MessageExt> {

  // 使用线程安全的 Map 存储接收到的消息（key = KEYS, value = MessageExt）
  private final Map<String, MessageExt> receivedMessages = new ConcurrentHashMap<>();

  // 用于等待消息到达的 CountDownLatch
  private volatile CountDownLatch latch;

  @Override
  public void onMessage(MessageExt message) {
    String keys = message.getKeys();
    String topic = message.getTopic();
    String msgId = message.getMsgId();

    log.info("✅ 测试 Consumer 收到消息: topic={}, keys={}, msgId={}", topic, keys, msgId);

    // 存储消息
    if (keys != null && !keys.isEmpty()) {
      receivedMessages.put(keys, message);
    } else {
      log.warn("收到没有 KEYS 的消息，msgId={}", msgId);
    }

    // 计数器减 1
    if (latch != null) {
      latch.countDown();
      log.debug("CountDownLatch count: {}", latch.getCount());
    }
  }

  /**
   * 检查是否接收到指定 key 的消息。
   *
   * @param key 消息的 KEYS 字段（对应 dedupKey）
   * @return 如果接收到该消息返回 true
   */
  public boolean hasMessage(String key) {
    return receivedMessages.containsKey(key);
  }

  /**
   * 获取指定 key 的消息。
   *
   * @param key 消息的 KEYS 字段（对应 dedupKey）
   * @return MessageExt 对象，如果不存在返回 null
   */
  public MessageExt getMessage(String key) {
    return receivedMessages.get(key);
  }

  /**
   * 获取接收到的消息总数。
   *
   * @return 消息数量
   */
  public int getMessageCount() {
    return receivedMessages.size();
  }

  /**
   * 清空接收到的所有消息。
   *
   * <p>每个测试开始前调用此方法，确保测试隔离。
   */
  public void clear() {
    receivedMessages.clear();
    latch = null;
    log.info("已清空测试 Consumer 接收到的消息");
  }

  /**
   * 设置 CountDownLatch 用于等待消息。
   *
   * <p>可选功能：如果需要精确控制等待消息数量，可以使用此方法。
   *
   * @param latch CountDownLatch 实例
   */
  public void setLatch(CountDownLatch latch) {
    this.latch = latch;
  }

  /**
   * 获取所有接收到的消息。
   *
   * @return 消息 Map（key=KEYS, value=MessageExt）
   */
  public Map<String, MessageExt> getAllMessages() {
    return new ConcurrentHashMap<>(receivedMessages);
  }
}
