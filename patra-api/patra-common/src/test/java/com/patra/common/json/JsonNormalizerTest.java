package com.patra.common.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class JsonNormalizerTest {

    private final ObjectMapper om = JsonMapperHolder.getObjectMapper();

    @Test
    void normalize_basic_object_and_string_policies() {
        JsonNormalizer.Config cfg = JsonNormalizer.Config.builder()
                .lowercaseFields(Set.of("name"))
                .keepEmptyWhitelist(Set.of("$.keep", "meta"))
                .build();
        JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "  Alice  "); // 会 trim + lowercase
        input.put("desc", "a   b\t c\n"); // 会 collapse 空白
        input.put("emptyStr", "  "); // -> 空串，经 removeEmpty 被移除
        input.put("keep", ""); // 白名单保留
        input.put("meta", Map.of()); // 空对象白名单保留

        JsonNormalizer.Result r = normalizer.normalize(input);
        String json0 = r.getCanonicalJson();
        assertThat(json0).contains("\"desc\":\"a b c\"")
                .contains("\"keep\":\"\"")
                .contains("\"meta\":{}")
                .contains("\"name\":\"alice\"");
        @SuppressWarnings("unchecked")
        Map<String, Object> canonical = (Map<String, Object>) r.getCanonicalValue();
        assertThat(canonical).containsKeys("name", "desc", "keep", "meta");
    }

    @Test
    void normalize_array_dedup_sort_vs_sequence_whitelist() {
        JsonNormalizer.Config cfg = JsonNormalizer.Config.builder()
                .sequenceFieldWhitelist(Set.of("seq")) // seq 数组保持原顺序，不去重/不全局排序
                .coerceTime(false)
                .coerceNumber(false)
                .coerceBoolean(JsonNormalizer.Config.CoerceBoolean.NONE)
                .build();
        JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("seq", List.of(3, 2, 2, 1));
        input.put("mix", List.of("b", "a", 2, 1, 1, Map.of("k", 1)));

        JsonNormalizer.Result r = normalizer.normalize(input);
        String canonicalJson = r.getCanonicalJson();
        // seq: 原样（不去重）
        assertThat(canonicalJson).contains("\"seq\":[3,2,2,1]");

        // mix: 去重 + 先按类型标签、再按序列化值排序 -> 数字(1,2) 然后 字符串(a,b) 然后 对象
        assertThat(canonicalJson).contains("\"mix\":[1,2,\"a\",\"b\",{\"k\":1}]");
    }

    @Test
    void forbidden_key_and_maxDepth_and_string_limit() throws Exception {
        JsonNormalizer.Config cfg = JsonNormalizer.Config.builder()
                .forbidKeys(Set.of("forbidden"))
                .maxDepth(1)
                .maxStringBytes(4)
                .build();
        JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

        // 禁用键
        Map<String, Object> input1 = Map.of("forbidden", 1);
        assertThatThrownBy(() -> normalizer.normalize(input1))
                .isInstanceOf(JsonNormalizer.JsonNormalizationException.class)
                .hasMessageContaining("Forbidden key");

        // 深度越界（根 depth=1，再下一层即越界）
        Map<String, Object> input2 = Map.of("a", Map.of("b", 1));
        assertThatThrownBy(() -> normalizer.normalize(input2))
                .isInstanceOf(JsonNormalizer.JsonNormalizationException.class)
                .hasMessageContaining("Max depth exceeded");

        // 字符串长度越界（包含 UTF-8 字节计算）——使用更大的深度限制以避开深度检查
        JsonNormalizer normalizer2 = JsonNormalizer.withConfig(JsonNormalizer.Config.builder()
                .forbidKeys(Set.of("forbidden"))
                .maxDepth(3)
                .maxStringBytes(4)
                .build());
        Map<String, Object> input3 = Map.of("long", "hello");
        assertThatThrownBy(() -> normalizer2.normalize(input3))
                .isInstanceOf(JsonNormalizer.JsonNormalizationException.class)
                .hasMessageContaining("String length exceeds limit");
    }

    @Test
    void coerce_boolean_number_time_and_sanitize_decimal() throws Exception {
        JsonNormalizer.Config cfg = JsonNormalizer.Config.builder()
                .coerceBoolean(JsonNormalizer.Config.CoerceBoolean.LOOSE)
                .coerceNumber(true)
                .coerceTime(true)
                .defaultZoneId(ZoneId.of("UTC"))
                .build();
        JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

        JsonNode node = om.readTree("{\n" +
                "  \"tTrue\": \"YES\",\n" +
                "  \"tFalse\": \"0\",\n" +
                "  \"ts1\": \"1700000000\",\n" +   // epoch 秒
                "  \"ts2\": \"1700000000000\",\n" + // epoch 毫秒
                "  \"fmt\": \"2024-05-01T12:34:56Z\",\n" +
                "  \"num\": \"001.2300\",\n" +
                "  \"num2\": 1,\n" +
                "  \"bin\": \"AQID\"\n" +
                "}");

        JsonNormalizer.Result r = normalizer.normalize(node);
        String json = r.getCanonicalJson();
        // 布尔被规整
        assertThat(json).contains("\"tTrue\":true");
        assertThat(json).contains("\"tFalse\":false");
        // 时间被格式化为 UTC 毫秒精度
        assertThat(json).containsPattern("\"ts1\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\"");
        assertThat(json).contains("\"fmt\":\"2024-05-01T12:34:56.000Z\"");
        // 数字字符串被 BigDecimal 化并去除尾随 0
        assertThat(json).contains("\"num\":1.23");
        // 数字 0/1 在 LOOSE 模式下会被当作布尔
        assertThat(json).contains("\"num2\":true");

        // binary：输入是 base64 → Jackson 解析为文本节点；此处不触发 binary 分支。
        // 为了覆盖 binary 分支，我们直接构造二进制节点
        JsonNormalizer.Result r2 = normalizer.normalize(JsonNodeFactory.instance.binaryNode(new byte[]{1,2,3}));
        assertThat(r2.getCanonicalJson()).isEqualTo("\"AQID\"");
    }

    @Test
    void nonFinite_number_should_throw_and_epoch_range_guard() {
        JsonNormalizer normalizer = JsonNormalizer.usingDefault();
        // 非有限浮点数
        assertThatThrownBy(() -> normalizer.normalize(JsonNodeFactory.instance.numberNode(Double.NaN)))
                .isInstanceOf(JsonNormalizer.JsonNormalizationException.class)
                .hasMessageContaining("Non-finite number");

        // epoch 过大触发保护
        JsonNormalizer.Config cfg = JsonNormalizer.Config.builder().build();
        JsonNormalizer n2 = JsonNormalizer.withConfig(cfg);
        assertThatThrownBy(() -> n2.normalize( Map.of("t", "99999999999999") ))
                .isInstanceOf(JsonNormalizer.JsonNormalizationException.class)
                .hasMessageContaining("Epoch value out of range");
    }

    @Test
    void matches_field_and_path_should_work_with_arrays() {
        JsonNormalizer.Config cfg = JsonNormalizer.Config.builder()
                .lowercaseFields(Set.of("items", "items.name", "$.items[].name"))
                .build();
        JsonNormalizer normalizer = JsonNormalizer.withConfig(cfg);

        Map<String, Object> input = Map.of(
                "items", List.of(
                        Map.of("name", " A "),
                        Map.of("name", "B ")
                )
        );
        JsonNormalizer.Result r = normalizer.normalize(input);
        assertThat(r.getCanonicalJson()).contains("\"name\":\"a\"").contains("\"name\":\"b\"");
    }
}
