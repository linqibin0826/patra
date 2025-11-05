package com.patra.ingest.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * 测试消息收集器。
 *
 * <p>用于集成测试中收集 RocketMQ Consumer 接收到的消息,支持多线程并发收集和断言验证。
 *
 * <h3>功能特性</h3>
 *
 * <ul>
 *   <li><strong>线程安全</strong>: 使用 ConcurrentHashMap 和 CopyOnWriteArrayList
 *   <li><strong>按键索引</strong>: 通过消息 KEYS 快速查找
 *   <li><strong>全量保存</strong>: 保留所有消息用于后续验证
 *   <li><strong>灵活查询</strong>: 支持按 key、topic、tags 查询
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @SpringBootTest
 * class RocketMqIntegrationTest {
 *
 *     private TestMessageCollector collector = new TestMessageCollector();
 *
 *     @Test
 *     @DisplayName("应该成功发送并接收消息")
 *     void shouldSendAndReceiveMessage() {
 *         // 1. 发送消息
 *         publisher.publish(outboxMessage);
 *
 *         // 2. 等待接收 (使用 Awaitility)
 *         await().atMost(5, SECONDS)
 *             .until(() -> collector.hasMessage("dedup-key-123"));
 *
 *         // 3. 验证消息内容
 *         MessageExt msg = collector.getMessage("dedup-key-123");
 *         assertThat(msg).isNotNull();
 *         assertThat(msg.getTags()).isEqualTo("CREATE");
 *     }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public class TestMessageCollector {

  /** 按 KEYS 索引的消息映射。用于快速查找。 */
  private final Map<String, MessageExt> messagesByKey = new ConcurrentHashMap<>();

  /** 所有接收到的消息列表。保持接收顺序。 */
  private final List<MessageExt> allMessages = new CopyOnWriteArrayList<>();

  /**
   * 收集一条消息。
   *
   * <p>线程安全方法,可在 RocketMQ Listener 回调中调用。
   *
   * <p><strong>注意</strong>: 如果消息没有 KEYS,将使用 MsgId 作为索引键。
   *
   * @param message 接收到的 RocketMQ 消息
   */
  public void collect(MessageExt message) {
    // 保存到全量列表
    allMessages.add(message);

    // 按 KEYS 索引 (优先使用 KEYS,fallback 到 MsgId)
    String key = message.getKeys() != null ? message.getKeys() : message.getMsgId();
    messagesByKey.put(key, message);
  }

  /**
   * 检查是否已收到指定 key 的消息。
   *
   * <p>用于 Awaitility 等待断言。
   *
   * @param key 消息的 KEYS 或 MsgId
   * @return 如果已收到返回 true,否则返回 false
   */
  public boolean hasMessage(String key) {
    return messagesByKey.containsKey(key);
  }

  /**
   * 获取指定 key 的消息。
   *
   * @param key 消息的 KEYS 或 MsgId
   * @return 消息实例,如果不存在返回 null
   */
  public MessageExt getMessage(String key) {
    return messagesByKey.get(key);
  }

  /**
   * 获取所有收集到的消息。
   *
   * @return 消息列表 (不可变视图)
   */
  public List<MessageExt> getAllMessages() {
    return new ArrayList<>(allMessages);
  }

  /**
   * 按 Topic 过滤消息。
   *
   * @param topic RocketMQ Topic 名称
   * @return 匹配的消息列表
   */
  public List<MessageExt> getMessagesByTopic(String topic) {
    return allMessages.stream().filter(msg -> topic.equals(msg.getTopic())).toList();
  }

  /**
   * 按 Tags 过滤消息。
   *
   * @param tags RocketMQ Tags (opType)
   * @return 匹配的消息列表
   */
  public List<MessageExt> getMessagesByTags(String tags) {
    return allMessages.stream().filter(msg -> tags.equals(msg.getTags())).toList();
  }

  /**
   * 获取已收集的消息总数。
   *
   * @return 消息数量
   */
  public int getMessageCount() {
    return allMessages.size();
  }

  /**
   * 清空所有已收集的消息。
   *
   * <p>用于测试用例之间的清理。
   */
  public void clear() {
    messagesByKey.clear();
    allMessages.clear();
  }

  /**
   * 检查是否为空 (未收到任何消息)。
   *
   * @return 如果没有消息返回 true
   */
  public boolean isEmpty() {
    return allMessages.isEmpty();
  }
}
