package dev.patra.tool.jpa.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.NullNode;

/// {@link JsonColumnCodec} 单元测试：钉住 JSON 列与领域值之间转换的空值语义。
@DisplayName("JsonColumnCodec 单元测试")
class JsonColumnCodecTest {

  private final JsonColumnCodec codec = new JsonColumnCodec(JsonMapper.builder().build());

  @Test
  @DisplayName("read：空白文本返回 null，非法 JSON 抛 IllegalArgumentException")
  void read_handles_blank_and_invalid() {
    assertThat(codec.read(null)).isNull();
    assertThat(codec.read("   ")).isNull();
    assertThat(codec.read("{\"a\":1}").get("a").asInt()).isEqualTo(1);
    assertThatThrownBy(() -> codec.read("{not json")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("write：null 节点返回 null，其余写成 JSON 文本")
  void write_handles_null() {
    assertThat(codec.write(null)).isNull();
    assertThat(codec.write(codec.read("{\"a\":1}"))).isEqualTo("{\"a\":1}");
  }

  @Test
  @DisplayName("fromMap：null 或空 Map 返回 null，Long 保持数字")
  void fromMap_keeps_long_as_number() {
    assertThat(codec.fromMap(null)).isNull();
    assertThat(codec.fromMap(Map.of())).isNull();

    Map<String, Object> map = new LinkedHashMap<>();
    map.put("from", 123L);
    JsonNode node = codec.fromMap(map);

    assertThat(node.get("from").isNumber()).isTrue();
    assertThat(node.get("from").asLong()).isEqualTo(123L);
  }

  @Test
  @DisplayName("toMap：null、JSON null、缺失节点都返回空 Map")
  void toMap_handles_absent_nodes() {
    assertThat(codec.toMap(null)).isEmpty();
    assertThat(codec.toMap(NullNode.getInstance())).isEmpty();
    assertThat(codec.toMap(codec.read("{}").path("missing"))).isEmpty();
    assertThat(codec.toMap(codec.read("{\"k\":\"v\"}"))).containsEntry("k", "v");
  }
}
