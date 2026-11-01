package dev.patra.analytics.infra.ingest.topic;

import dev.patra.analytics.domain.ingest.exception.IngestUnavailableException;
import dev.patra.analytics.domain.ingest.port.topic.TopicIdPort;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.stereotype.Component;

/// 用 AdminClient 的 `describeTopics` 取 topic ID，按 topic 名缓存（topic 重建要重启服务，见上线手册）。
@Component
public class KafkaTopicIdAdapter implements TopicIdPort {

  private static final long TIMEOUT_SECONDS = 10;

  private final Admin admin;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  /// 创建适配器。
  ///
  /// @param admin Kafka 管理客户端
  public KafkaTopicIdAdapter(Admin admin) {
    this.admin = admin;
  }

  /// 取 topic 的 UUID。
  ///
  /// @param topic topic 名
  /// @return topic ID
  @Override
  public String topicId(String topic) {
    return cache.computeIfAbsent(topic, this::describe);
  }

  /// 查 broker。
  ///
  /// @param topic topic 名
  /// @return topic ID
  private String describe(String topic) {
    try {
      return admin
          .describeTopics(List.of(topic))
          .allTopicNames()
          .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
          .get(topic)
          .topicId()
          .toString();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IngestUnavailableException("查 topic ID 时被中断: " + topic, e);
    } catch (Exception e) {
      throw new IngestUnavailableException("查 topic ID 失败: " + topic, e);
    }
  }
}
