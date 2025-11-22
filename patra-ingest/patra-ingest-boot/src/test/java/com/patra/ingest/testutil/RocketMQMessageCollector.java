package com.patra.ingest.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// RocketMQ 测试消息收集器（统一版本）。
///
/// 用于集成测试和 E2E 测试中收集 RocketMQ Consumer 接收到的消息,支持多线程并发收集和断言验证。
///
/// ### 功能特性
///
/// - **线程安全**: 使用 ConcurrentHashMap 和 CopyOnWriteArrayList
///   - **按键索引**: 通过消息 KEYS 快速查找
///   - **全量保存**: 保留所有消息用于后续验证
///   - **灵活查询**: 支持按 key、topic、tags 查询
///   - **可选计数器**: 支持 CountDownLatch 精确控制等待
///
/// ### 使用场景
///
/// - **集成测试**: 配合 Spring 管理的 @RocketMQMessageListener 使用
///   - **E2E 测试**: 配合手动创建的 DefaultMQPushConsumer 使用
///
/// ### 使用示例 1: 集成测试（Spring Consumer）
///
/// ```java
/// @Component
/// @RocketMQMessageListener(topic = "TEST_TOPIC", consumerGroup = "test-group")
/// class TestConsumer implements RocketMQListener<MessageExt> {
///     @Autowired
///     private RocketMQMessageCollector collector;
///
///     @Override
///     public void onMessage(MessageExt message) {
///         collector.collect(message);
///
/// @Test
/// void shouldReceiveMessage() {
///     publisher.publish(message);
///     await().atMost(5, SECONDS)
///         .until(() -> collector.hasMessage("dedup-key"));
///     assertThat(collector.getMessage("dedup-key").getTags()).isEqualTo("CREATE");
/// ```
///
/// ### 使用示例 2: E2E 测试（手动 Consumer）
///
/// ```java
/// private RocketMQMessageCollector collector = new RocketMQMessageCollector();
/// private DefaultMQPushConsumer consumer;
///
/// @BeforeEach
/// void setUp() throws Exception {
///     consumer = new DefaultMQPushConsumer("test-group");
///     consumer.setNamesrvAddr(namesrvAddr);
///     consumer.subscribe("TEST_TOPIC", "*");
///     consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
///         messages.forEach(collector::collect);
///         return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;);
///     consumer.start();
///
/// @AfterEach
/// void tearDown() {
///     if (consumer != null) consumer.shutdown();
///     collector.clear();
/// ```
///
/// @author linqibin
/// @since 0.1.0
public class RocketMQMessageCollector {

  private static final Logger log = LoggerFactory.getLogger(RocketMQMessageCollector.class);

  /// 按 KEYS 索引的消息映射。用于快速查找。
  private final Map<String, MessageExt> messagesByKey = new ConcurrentHashMap<>();

  /// 所有接收到的消息列表。保持接收顺序。
  private final List<MessageExt> allMessages = new CopyOnWriteArrayList<>();

  /// 用于等待消息到达的 CountDownLatch（可选）。
  private volatile CountDownLatch latch;

  /// 收集一条消息。
  ///
  /// 线程安全方法,可在 RocketMQ Listener 回调中调用。
  ///
  /// **注意**: 如果消息没有 KEYS,将使用 MsgId 作为索引键。
  ///
  /// @param message 接收到的 RocketMQ 消息
  public void collect(MessageExt message) {
    // 保存到全量列表
    allMessages.add(message);

    // 按 KEYS 索引 (优先使用 KEYS,fallback 到 MsgId)
    String key = message.getKeys() != null ? message.getKeys() : message.getMsgId();
    messagesByKey.put(key, message);

    // 日志记录（可选，用于调试）
    if (log.isDebugEnabled()) {
      log.debug(
          "✅ 消息收集器收到消息: topic={}, keys={}, msgId={}",
          message.getTopic(),
          message.getKeys(),
          message.getMsgId());
    }

    // 计数器减 1
    if (latch != null) {
      latch.countDown();
      if (log.isDebugEnabled()) {
        log.debug("CountDownLatch count: {}", latch.getCount());
      }
    }
  }

  /// 检查是否已收到指定 key 的消息。
  ///
  /// 用于 Awaitility 等待断言。
  ///
  /// @param key 消息的 KEYS 或 MsgId
  /// @return 如果已收到返回 true,否则返回 false
  public boolean hasMessage(String key) {
    return messagesByKey.containsKey(key);
  }

  /// 获取指定 key 的消息。
  ///
  /// @param key 消息的 KEYS 或 MsgId
  /// @return 消息实例,如果不存在返回 null
  public MessageExt getMessage(String key) {
    return messagesByKey.get(key);
  }

  /// 获取所有收集到的消息。
  ///
  /// @return 消息列表（不可变视图）
  public List<MessageExt> getAllMessages() {
    return new ArrayList<>(allMessages);
  }

  /// 按 Topic 过滤消息。
  ///
  /// @param topic RocketMQ Topic 名称
  /// @return 匹配的消息列表
  public List<MessageExt> getMessagesByTopic(String topic) {
    return allMessages.stream().filter(msg -> topic.equals(msg.getTopic())).toList();
  }

  /// 按 Tags 过滤消息。
  ///
  /// @param tags RocketMQ Tags (opType)
  /// @return 匹配的消息列表
  public List<MessageExt> getMessagesByTags(String tags) {
    return allMessages.stream().filter(msg -> tags.equals(msg.getTags())).toList();
  }

  /// 获取已收集的消息总数。
  ///
  /// @return 消息数量
  public int getMessageCount() {
    return allMessages.size();
  }

  /// 清空所有已收集的消息。
  ///
  /// 用于测试用例之间的清理。
  public void clear() {
    messagesByKey.clear();
    allMessages.clear();
    latch = null;
    if (log.isDebugEnabled()) {
      log.debug("已清空消息收集器中的所有消息");
    }
  }

  /// 检查是否为空 (未收到任何消息)。
  ///
  /// @return 如果没有消息返回 true
  public boolean isEmpty() {
    return allMessages.isEmpty();
  }

  /// 设置 CountDownLatch 用于等待消息。
  ///
  /// 可选功能：如果需要精确控制等待消息数量，可以使用此方法。
  ///
  /// @param latch CountDownLatch 实例
  public void setLatch(CountDownLatch latch) {
    this.latch = latch;
  }
}
