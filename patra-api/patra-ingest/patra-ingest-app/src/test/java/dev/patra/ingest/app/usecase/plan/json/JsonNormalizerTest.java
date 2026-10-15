package dev.patra.ingest.app.usecase.plan.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/// JsonNormalizer 单元测试。
///
/// 用固定输入的 canonical JSON 快照钉住规范化输出：输出参与哈希，不允许随应用的 Jackson 配置漂移。
@DisplayName("JsonNormalizer")
class JsonNormalizerTest {

  /// 构造固定输入：键乱序、含 `Long`、嵌套 Map、数字字符串和需要折叠空白的字符串。
  ///
  /// @return 固定输入
  private static Map<String, Object> fixedInput() {
    Map<String, Object> nested = new LinkedHashMap<>();
    nested.put("z", "  x   y ");
    nested.put("y", "42");
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("b", 12345678901234L);
    input.put("a", nested);
    return input;
  }

  @Test
  @DisplayName("默认配置输出固定的 canonical JSON 快照")
  void should_produce_fixed_canonical_json_with_default_config() {
    JsonNormalizerResult result = JsonNormalizer.normalizeDefault(fixedInput());

    assertThat(result.getCanonicalJson())
        .isEqualTo("{\"a\":{\"y\":42,\"z\":\"x y\"},\"b\":12345678901234}");
    assertThat(result.getHashMaterial())
        .isEqualTo(result.getCanonicalJson().getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("关闭数字强制时 Long 仍输出为数字而数字字符串保持字符串")
  void should_write_long_as_number_when_number_coercion_disabled() {
    JsonNormalizer normalizer =
        JsonNormalizer.withConfig(JsonNormalizerConfig.builder().coerceNumber(false).build());

    JsonNormalizerResult result = normalizer.normalize(fixedInput());

    assertThat(result.getCanonicalJson())
        .isEqualTo("{\"a\":{\"y\":\"42\",\"z\":\"x y\"},\"b\":12345678901234}");
  }

  @Test
  @DisplayName("重复规范化同一输入结果稳定")
  void should_produce_stable_result_across_invocations() {
    JsonNormalizer normalizer = JsonNormalizer.usingDefault();

    JsonNormalizerResult first = normalizer.normalize(fixedInput());
    JsonNormalizerResult second = JsonNormalizer.usingDefault().normalize(fixedInput());

    assertThat(second.getCanonicalJson()).isEqualTo(first.getCanonicalJson());
    assertThat(second.getHashMaterial()).isEqualTo(first.getHashMaterial());
  }

  @Test
  @DisplayName("显式传入 JsonMapper 时与内置默认 mapper 输出一致")
  void should_match_default_output_when_mapper_supplied() {
    JsonNormalizer normalizer =
        JsonNormalizer.withMapper(
            JsonMapper.builder().build(), JsonNormalizerConfig.builder().build());

    JsonNormalizerResult result = normalizer.normalize(fixedInput());

    assertThat(result.getCanonicalJson())
        .isEqualTo(JsonNormalizer.normalizeDefault(fixedInput()).getCanonicalJson());
  }
}
