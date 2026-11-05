package dev.patra.analytics.collect;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/// 采集集成测试用的样例：从 `patra-analytics-api` 的 `analytics/examples/` 读，换上新的 `event_id`。
public final class CollectExamples {

  /// 解析与生成 JSON
  public static final JsonMapper MAPPER = JsonMapper.builder().build();

  private CollectExamples() {}

  /// 读一个样例并换上新的 event_id（同一 JVM 的用例共用 Kafka 容器，按 ID 区分）。
  ///
  /// @param file `valid/` 下的文件名，或以 `invalid/` 开头的路径
  /// @return 样例
  public static ObjectNode example(String file) {
    String path = file.startsWith("invalid/") ? file : "valid/" + file;
    try (InputStream in =
        CollectExamples.class.getClassLoader().getResourceAsStream("analytics/examples/" + path)) {
      ObjectNode node =
          (ObjectNode) MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
      node.put("event_id", UUID.randomUUID().toString());
      return node;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// 把几条事件拼成采集批次。
  ///
  /// @param events 事件
  /// @return 请求体
  public static String batch(JsonNode... events) {
    ObjectNode body = MAPPER.createObjectNode();
    body.putArray("events").addAll(List.of(events));
    return MAPPER.writeValueAsString(body);
  }
}
