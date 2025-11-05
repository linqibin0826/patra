package com.patra.ingest.domain.model.vo.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskRunCheckpoint 值对象单元测试")
class TaskRunCheckpointTest {

  @Nested
  @DisplayName("构造器测试")
  class ConstructorTests {

    @Test
    @DisplayName("应该使用有效JSON创建检查点")
    void shouldCreateCheckpointWithValidJson() {
      // Given
      String json = "{\"page\": 5, \"cursor\": \"abc123\"}";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(json);

      // Then
      assertThat(checkpoint.raw()).isEqualTo(json);
    }

    @Test
    @DisplayName("应该允许创建 null 检查点")
    void shouldAllowNullCheckpoint() {
      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(null);

      // Then
      assertThat(checkpoint.raw()).isNull();
    }

    @Test
    @DisplayName("应该将空白字符串规范化为 null")
    void shouldNormalizeBlankStringToNull() {
      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("   ");

      // Then
      assertThat(checkpoint.raw()).isNull();
    }

    @Test
    @DisplayName("应该将空字符串规范化为 null")
    void shouldNormalizeEmptyStringToNull() {
      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("");

      // Then
      assertThat(checkpoint.raw()).isNull();
    }

    @Test
    @DisplayName("应该将制表符和换行符规范化为 null")
    void shouldNormalizeTabsAndNewlinesToNull() {
      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("\t\n\r");

      // Then
      assertThat(checkpoint.raw()).isNull();
    }

    @Test
    @DisplayName("应该保留包含非空白字符的字符串")
    void shouldPreserveStringWithNonWhitespaceCharacters() {
      // Given
      String json = "  {\"data\": \"value\"}  ";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(json);

      // Then
      assertThat(checkpoint.raw()).isEqualTo(json);
    }
  }

  @Nested
  @DisplayName("静态工厂方法测试")
  class FactoryMethodTests {

    @Test
    @DisplayName("empty() 应该返回 null raw 的检查点")
    void emptyShouldReturnNullRaw() {
      // When
      TaskRunCheckpoint empty = TaskRunCheckpoint.empty();

      // Then
      assertThat(empty.raw()).isNull();
    }

    @Test
    @DisplayName("empty() 应该返回等同于 null 构造的对象")
    void emptyShouldReturnEquivalentToNullConstructor() {
      // When
      TaskRunCheckpoint empty = TaskRunCheckpoint.empty();
      TaskRunCheckpoint nullCheckpoint = new TaskRunCheckpoint(null);

      // Then
      assertThat(empty).isEqualTo(nullCheckpoint);
    }
  }

  @Nested
  @DisplayName("isPresent() 方法测试")
  class IsPresentMethodTests {

    @Test
    @DisplayName("包含有效JSON时应该返回 true")
    void shouldReturnTrueWhenContainsValidJson() {
      // Given
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("{\"page\": 5}");

      // When
      boolean present = checkpoint.isPresent();

      // Then
      assertThat(present).isTrue();
    }

    @Test
    @DisplayName("包含任意非空白内容时应该返回 true")
    void shouldReturnTrueWhenContainsNonBlankContent() {
      // Given
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("checkpoint-data");

      // When
      boolean present = checkpoint.isPresent();

      // Then
      assertThat(present).isTrue();
    }

    @Test
    @DisplayName("raw 为 null 时应该返回 false")
    void shouldReturnFalseWhenRawIsNull() {
      // Given
      TaskRunCheckpoint checkpoint = TaskRunCheckpoint.empty();

      // When
      boolean present = checkpoint.isPresent();

      // Then
      assertThat(present).isFalse();
    }

    @Test
    @DisplayName("构造时传入空白字符串应该返回 false")
    void shouldReturnFalseWhenConstructedWithBlankString() {
      // Given
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("   ");

      // When
      boolean present = checkpoint.isPresent();

      // Then
      assertThat(present).isFalse();
    }

    @Test
    @DisplayName("构造时传入空字符串应该返回 false")
    void shouldReturnFalseWhenConstructedWithEmptyString() {
      // Given
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("");

      // When
      boolean present = checkpoint.isPresent();

      // Then
      assertThat(present).isFalse();
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("相同内容的实例应该相等")
    void instancesWithSameContentShouldBeEqual() {
      // Given
      TaskRunCheckpoint checkpoint1 = new TaskRunCheckpoint("{\"page\": 5}");
      TaskRunCheckpoint checkpoint2 = new TaskRunCheckpoint("{\"page\": 5}");

      // Then
      assertThat(checkpoint1).isEqualTo(checkpoint2);
      assertThat(checkpoint1.hashCode()).isEqualTo(checkpoint2.hashCode());
    }

    @Test
    @DisplayName("不同内容的实例应该不相等")
    void instancesWithDifferentContentShouldNotBeEqual() {
      // Given
      TaskRunCheckpoint checkpoint1 = new TaskRunCheckpoint("{\"page\": 5}");
      TaskRunCheckpoint checkpoint2 = new TaskRunCheckpoint("{\"page\": 6}");

      // Then
      assertThat(checkpoint1).isNotEqualTo(checkpoint2);
    }

    @Test
    @DisplayName("两个空检查点应该相等")
    void twoEmptyCheckpointsShouldBeEqual() {
      // Given
      TaskRunCheckpoint empty1 = TaskRunCheckpoint.empty();
      TaskRunCheckpoint empty2 = TaskRunCheckpoint.empty();

      // Then
      assertThat(empty1).isEqualTo(empty2);
      assertThat(empty1.hashCode()).isEqualTo(empty2.hashCode());
    }

    @Test
    @DisplayName("空检查点与 null raw 检查点应该相等")
    void emptyCheckpointShouldEqualNullRawCheckpoint() {
      // Given
      TaskRunCheckpoint empty = TaskRunCheckpoint.empty();
      TaskRunCheckpoint nullCheckpoint = new TaskRunCheckpoint(null);

      // Then
      assertThat(empty).isEqualTo(nullCheckpoint);
    }

    @Test
    @DisplayName("空白字符串检查点与空检查点应该相等")
    void blankStringCheckpointShouldEqualEmptyCheckpoint() {
      // Given
      TaskRunCheckpoint blankCheckpoint = new TaskRunCheckpoint("   ");
      TaskRunCheckpoint empty = TaskRunCheckpoint.empty();

      // Then
      assertThat(blankCheckpoint).isEqualTo(empty);
    }

    @Test
    @DisplayName("toString() 应该包含 raw 字段信息")
    void toStringShouldContainRawField() {
      // Given
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint("{\"page\": 5}");

      // When
      String result = checkpoint.toString();

      // Then
      assertThat(result).contains("TaskRunCheckpoint").contains("{\"page\": 5}");
    }

    @Test
    @DisplayName("应该支持作为 Map 的键")
    void shouldWorkAsMapKey() {
      // Given
      var map = new java.util.HashMap<TaskRunCheckpoint, String>();
      TaskRunCheckpoint key1 = new TaskRunCheckpoint("{\"page\": 5}");
      TaskRunCheckpoint key2 = new TaskRunCheckpoint("{\"page\": 5}");

      // When
      map.put(key1, "value1");

      // Then
      assertThat(map.get(key2)).isEqualTo("value1"); // 相同值可以检索
      assertThat(map).containsKey(key1);
      assertThat(map).containsKey(key2);
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理复杂嵌套的JSON结构")
    void shouldHandleComplexNestedJson() {
      // Given
      String complexJson =
          """
          {
            "page": 5,
            "cursor": "abc123",
            "filters": {
              "date": "2025-01-01",
              "tags": ["tag1", "tag2"]
            },
            "metadata": {
              "nested": {
                "deeply": {
                  "value": 42
                }
              }
            }
          }
          """;

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(complexJson);

      // Then
      assertThat(checkpoint.raw()).isEqualTo(complexJson);
      assertThat(checkpoint.isPresent()).isTrue();
    }

    @Test
    @DisplayName("应该处理非常长的JSON字符串")
    void shouldHandleVeryLongJson() {
      // Given
      String longJson = "{\"data\": \"" + "x".repeat(10000) + "\"}";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(longJson);

      // Then
      assertThat(checkpoint.raw()).hasSize(longJson.length());
      assertThat(checkpoint.isPresent()).isTrue();
    }

    @Test
    @DisplayName("应该处理包含特殊字符的JSON")
    void shouldHandleJsonWithSpecialCharacters() {
      // Given
      String jsonWithSpecialChars = "{\"data\": \"测试\\n\\t\\r\\\"\"}";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(jsonWithSpecialChars);

      // Then
      assertThat(checkpoint.raw()).isEqualTo(jsonWithSpecialChars);
      assertThat(checkpoint.isPresent()).isTrue();
    }

    @Test
    @DisplayName("应该处理格式不正确的JSON（不进行验证）")
    void shouldHandleInvalidJsonWithoutValidation() {
      // Given
      String invalidJson = "{invalid json}";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(invalidJson);

      // Then
      assertThat(checkpoint.raw()).isEqualTo(invalidJson);
      assertThat(checkpoint.isPresent()).isTrue();
    }

    @Test
    @DisplayName("应该处理单个字符的内容")
    void shouldHandleSingleCharacterContent() {
      // Given
      String singleChar = "a";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(singleChar);

      // Then
      assertThat(checkpoint.raw()).isEqualTo("a");
      assertThat(checkpoint.isPresent()).isTrue();
    }

    @Test
    @DisplayName("应该处理只包含空格但前后有非空白字符的字符串")
    void shouldHandleStringWithLeadingTrailingSpaces() {
      // Given
      String withSpaces = "  data  ";

      // When
      TaskRunCheckpoint checkpoint = new TaskRunCheckpoint(withSpaces);

      // Then
      assertThat(checkpoint.raw()).isEqualTo("  data  ");
      assertThat(checkpoint.isPresent()).isTrue();
    }
  }
}
