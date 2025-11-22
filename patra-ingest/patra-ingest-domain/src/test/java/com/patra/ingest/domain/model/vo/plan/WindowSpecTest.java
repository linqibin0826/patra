package com.patra.ingest.domain.model.vo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// WindowSpec 单元测试。
/// 
/// 测试覆盖:
/// 
/// - 所有实现类的构造器验证逻辑
///   - 工厂方法
///   - Record 语义(equals/hashCode/toString)
///   - toMap() 序列化
///   - fromMap() 反序列化
///   - 边界条件和异常场景
/// 
/// @author linqibin
@DisplayName("WindowSpec 单元测试")
class WindowSpecTest {

  @Nested
  @DisplayName("Time 窗口规范测试")
  class TimeWindowTest {

    @Nested
    @DisplayName("构造器验证测试")
    class ConstructorValidationTest {

      @Test
      @DisplayName("应该成功创建合法的时间窗口")
      void shouldCreateValidTimeWindow() {
        // Given
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");

        // When
        WindowSpec.Time window = new WindowSpec.Time(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
        assertThat(window.strategy()).isEqualTo(SliceStrategy.TIME);
      }

      @Test
      @DisplayName("应该允许 from 等于 to")
      void shouldAllowFromEqualToTo() {
        // Given
        Instant sameTime = Instant.parse("2024-06-15T12:00:00Z");

        // When
        WindowSpec.Time window = new WindowSpec.Time(sameTime, sameTime);

        // Then
        assertThat(window.from()).isEqualTo(sameTime);
        assertThat(window.to()).isEqualTo(sameTime);
      }

      @Test
      @DisplayName("应该拒绝 from 为 null")
      void shouldRejectNullFrom() {
        // Given
        Instant to = Instant.now();

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.Time(null, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("时间窗口要求from和to都非空");
      }

      @Test
      @DisplayName("应该拒绝 to 为 null")
      void shouldRejectNullTo() {
        // Given
        Instant from = Instant.now();

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.Time(from, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("时间窗口要求from和to都非空");
      }

      @Test
      @DisplayName("应该拒绝 from 晚于 to")
      void shouldRejectFromAfterTo() {
        // Given
        Instant from = Instant.parse("2024-12-31T23:59:59Z");
        Instant to = Instant.parse("2024-01-01T00:00:00Z");

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.Time(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("from必须早于或等于to");
      }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

      @Test
      @DisplayName("应该通过 ofTime 工厂方法创建实例")
      void shouldCreateViaOfTime() {
        // Given
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");

        // When
        WindowSpec.Time window = WindowSpec.ofTime(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
      }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTest {

      @Test
      @DisplayName("应该正确序列化为 Map 格式")
      void shouldSerializeToMapCorrectly() {
        // Given
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");
        WindowSpec.Time window = new WindowSpec.Time(from, to);

        // When
        Map<String, Object> map = window.toMap();

        // Then
        assertThat(map).containsEntry("strategy", "TIME");

        @SuppressWarnings("unchecked")
        Map<String, Object> windowMap = (Map<String, Object>) map.get("window");
        assertThat(windowMap).isNotNull();
        assertThat(windowMap.get("from")).isEqualTo("2024-01-01T00:00:00Z");
        assertThat(windowMap.get("to")).isEqualTo("2024-12-31T23:59:59Z");
        assertThat(windowMap.get("timezone")).isEqualTo("UTC");

        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryMap = (Map<String, Object>) windowMap.get("boundary");
        assertThat(boundaryMap).containsEntry("from", "CLOSED").containsEntry("to", "OPEN");
      }
    }

    @Nested
    @DisplayName("Record 语义测试")
    class RecordSemanticsTest {

      @Test
      @DisplayName("应该正确实现 equals")
      void shouldImplementEqualsCorrectly() {
        // Given
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");
        WindowSpec.Time window1 = new WindowSpec.Time(from, to);
        WindowSpec.Time window2 = new WindowSpec.Time(from, to);
        WindowSpec.Time window3 = new WindowSpec.Time(from, Instant.parse("2024-06-30T23:59:59Z"));

        // Then
        assertThat(window1).isEqualTo(window2);
        assertThat(window1).isNotEqualTo(window3);
      }

      @Test
      @DisplayName("应该正确实现 hashCode")
      void shouldImplementHashCodeCorrectly() {
        // Given
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");
        WindowSpec.Time window1 = new WindowSpec.Time(from, to);
        WindowSpec.Time window2 = new WindowSpec.Time(from, to);

        // Then
        assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
      }

      @Test
      @DisplayName("应该正确实现 toString")
      void shouldImplementToStringCorrectly() {
        // Given
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2024-12-31T23:59:59Z");
        WindowSpec.Time window = new WindowSpec.Time(from, to);

        // When
        String str = window.toString();

        // Then
        assertThat(str)
            .contains("Time")
            .contains("2024-01-01T00:00:00Z")
            .contains("2024-12-31T23:59:59Z");
      }
    }
  }

  @Nested
  @DisplayName("IdRange 窗口规范测试")
  class IdRangeWindowTest {

    @Nested
    @DisplayName("构造器验证测试")
    class ConstructorValidationTest {

      @Test
      @DisplayName("应该成功创建合法的 ID 范围窗口")
      void shouldCreateValidIdRangeWindow() {
        // Given
        Long from = 1000000L;
        Long to = 2000000L;

        // When
        WindowSpec.IdRange window = new WindowSpec.IdRange(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
        assertThat(window.strategy()).isEqualTo(SliceStrategy.ID_RANGE);
      }

      @Test
      @DisplayName("应该允许 from 等于 to")
      void shouldAllowFromEqualToTo() {
        // Given
        Long sameId = 1000000L;

        // When
        WindowSpec.IdRange window = new WindowSpec.IdRange(sameId, sameId);

        // Then
        assertThat(window.from()).isEqualTo(sameId);
        assertThat(window.to()).isEqualTo(sameId);
      }

      @Test
      @DisplayName("应该拒绝 from 为 null")
      void shouldRejectNullFrom() {
        // Given
        Long to = 2000000L;

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.IdRange(null, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ID范围窗口要求from和to都非空");
      }

      @Test
      @DisplayName("应该拒绝 to 为 null")
      void shouldRejectNullTo() {
        // Given
        Long from = 1000000L;

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.IdRange(from, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ID范围窗口要求from和to都非空");
      }

      @Test
      @DisplayName("应该拒绝 from 大于 to")
      void shouldRejectFromGreaterThanTo() {
        // Given
        Long from = 2000000L;
        Long to = 1000000L;

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.IdRange(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("from必须小于或等于to");
      }

      @Test
      @DisplayName("应该接受负数 ID")
      void shouldAcceptNegativeIds() {
        // Given
        Long from = -1000L;
        Long to = -500L;

        // When
        WindowSpec.IdRange window = new WindowSpec.IdRange(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
      }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

      @Test
      @DisplayName("应该通过 ofIdRange 工厂方法创建实例")
      void shouldCreateViaOfIdRange() {
        // Given
        Long from = 1000000L;
        Long to = 2000000L;

        // When
        WindowSpec.IdRange window = WindowSpec.ofIdRange(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
      }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTest {

      @Test
      @DisplayName("应该正确序列化为 Map 格式")
      void shouldSerializeToMapCorrectly() {
        // Given
        WindowSpec.IdRange window = new WindowSpec.IdRange(1000000L, 2000000L);

        // When
        Map<String, Object> map = window.toMap();

        // Then
        assertThat(map).containsEntry("strategy", "ID_RANGE");

        @SuppressWarnings("unchecked")
        Map<String, Object> windowMap = (Map<String, Object>) map.get("window");
        assertThat(windowMap).isNotNull();
        assertThat(windowMap.get("from")).isEqualTo(1000000L);
        assertThat(windowMap.get("to")).isEqualTo(2000000L);
      }
    }

    @Nested
    @DisplayName("Record 语义测试")
    class RecordSemanticsTest {

      @Test
      @DisplayName("应该正确实现 equals")
      void shouldImplementEqualsCorrectly() {
        // Given
        WindowSpec.IdRange window1 = new WindowSpec.IdRange(1000000L, 2000000L);
        WindowSpec.IdRange window2 = new WindowSpec.IdRange(1000000L, 2000000L);
        WindowSpec.IdRange window3 = new WindowSpec.IdRange(1000000L, 1500000L);

        // Then
        assertThat(window1).isEqualTo(window2);
        assertThat(window1).isNotEqualTo(window3);
      }

      @Test
      @DisplayName("应该正确实现 hashCode")
      void shouldImplementHashCodeCorrectly() {
        // Given
        WindowSpec.IdRange window1 = new WindowSpec.IdRange(1000000L, 2000000L);
        WindowSpec.IdRange window2 = new WindowSpec.IdRange(1000000L, 2000000L);

        // Then
        assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
      }

      @Test
      @DisplayName("应该正确实现 toString")
      void shouldImplementToStringCorrectly() {
        // Given
        WindowSpec.IdRange window = new WindowSpec.IdRange(1000000L, 2000000L);

        // When
        String str = window.toString();

        // Then
        assertThat(str).contains("IdRange").contains("1000000").contains("2000000");
      }
    }
  }

  @Nested
  @DisplayName("CursorLandmark 窗口规范测试")
  class CursorLandmarkWindowTest {

    @Nested
    @DisplayName("构造器验证测试")
    class ConstructorValidationTest {

      @Test
      @DisplayName("应该成功创建合法的游标地标窗口")
      void shouldCreateValidCursorLandmarkWindow() {
        // Given
        String from = "cursor_token_1";
        String to = "cursor_token_2";

        // When
        WindowSpec.CursorLandmark window = new WindowSpec.CursorLandmark(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
        assertThat(window.strategy()).isEqualTo(SliceStrategy.CURSOR_LANDMARK);
      }

      @Test
      @DisplayName("应该拒绝 from 为 null")
      void shouldRejectNullFrom() {
        // Given
        String to = "cursor_token";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.CursorLandmark(null, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("游标地标窗口要求from和to都非空且非空白");
      }

      @Test
      @DisplayName("应该拒绝 to 为 null")
      void shouldRejectNullTo() {
        // Given
        String from = "cursor_token";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.CursorLandmark(from, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("游标地标窗口要求from和to都非空且非空白");
      }

      @Test
      @DisplayName("应该拒绝 from 为空白字符串")
      void shouldRejectBlankFrom() {
        // Given
        String from = "   ";
        String to = "cursor_token";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.CursorLandmark(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("游标地标窗口要求from和to都非空且非空白");
      }

      @Test
      @DisplayName("应该拒绝 to 为空白字符串")
      void shouldRejectBlankTo() {
        // Given
        String from = "cursor_token";
        String to = "   ";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.CursorLandmark(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("游标地标窗口要求from和to都非空且非空白");
      }

      @Test
      @DisplayName("应该拒绝 from 为空字符串")
      void shouldRejectEmptyFrom() {
        // Given
        String from = "";
        String to = "cursor_token";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.CursorLandmark(from, to))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("游标地标窗口要求from和to都非空且非空白");
      }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

      @Test
      @DisplayName("应该通过 ofCursor 工厂方法创建实例")
      void shouldCreateViaOfCursor() {
        // Given
        String from = "cursor_token_1";
        String to = "cursor_token_2";

        // When
        WindowSpec.CursorLandmark window = WindowSpec.ofCursor(from, to);

        // Then
        assertThat(window.from()).isEqualTo(from);
        assertThat(window.to()).isEqualTo(to);
      }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTest {

      @Test
      @DisplayName("应该正确序列化为 Map 格式")
      void shouldSerializeToMapCorrectly() {
        // Given
        WindowSpec.CursorLandmark window =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");

        // When
        Map<String, Object> map = window.toMap();

        // Then
        assertThat(map).containsEntry("strategy", "CURSOR_LANDMARK");

        @SuppressWarnings("unchecked")
        Map<String, Object> windowMap = (Map<String, Object>) map.get("window");
        assertThat(windowMap).isNotNull();
        assertThat(windowMap.get("from")).isEqualTo("cursor_token_1");
        assertThat(windowMap.get("to")).isEqualTo("cursor_token_2");
      }
    }

    @Nested
    @DisplayName("Record 语义测试")
    class RecordSemanticsTest {

      @Test
      @DisplayName("应该正确实现 equals")
      void shouldImplementEqualsCorrectly() {
        // Given
        WindowSpec.CursorLandmark window1 =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");
        WindowSpec.CursorLandmark window2 =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");
        WindowSpec.CursorLandmark window3 =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_3");

        // Then
        assertThat(window1).isEqualTo(window2);
        assertThat(window1).isNotEqualTo(window3);
      }

      @Test
      @DisplayName("应该正确实现 hashCode")
      void shouldImplementHashCodeCorrectly() {
        // Given
        WindowSpec.CursorLandmark window1 =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");
        WindowSpec.CursorLandmark window2 =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");

        // Then
        assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
      }

      @Test
      @DisplayName("应该正确实现 toString")
      void shouldImplementToStringCorrectly() {
        // Given
        WindowSpec.CursorLandmark window =
            new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");

        // When
        String str = window.toString();

        // Then
        assertThat(str)
            .contains("CursorLandmark")
            .contains("cursor_token_1")
            .contains("cursor_token_2");
      }
    }
  }

  @Nested
  @DisplayName("VolumeBudget 窗口规范测试")
  class VolumeBudgetWindowTest {

    @Nested
    @DisplayName("构造器验证测试")
    class ConstructorValidationTest {

      @Test
      @DisplayName("应该成功创建合法的容量预算窗口")
      void shouldCreateValidVolumeBudgetWindow() {
        // Given
        Integer limit = 100000;
        String unit = "RECORDS";

        // When
        WindowSpec.VolumeBudget window = new WindowSpec.VolumeBudget(limit, unit);

        // Then
        assertThat(window.limit()).isEqualTo(limit);
        assertThat(window.unit()).isEqualTo(unit);
        assertThat(window.strategy()).isEqualTo(SliceStrategy.VOLUME_BUDGET);
      }

      @Test
      @DisplayName("应该拒绝 limit 为 null")
      void shouldRejectNullLimit() {
        // Given
        String unit = "RECORDS";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.VolumeBudget(null, unit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("容量限制必须为正数");
      }

      @Test
      @DisplayName("应该拒绝 limit 为 0")
      void shouldRejectZeroLimit() {
        // Given
        Integer limit = 0;
        String unit = "RECORDS";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.VolumeBudget(limit, unit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("容量限制必须为正数");
      }

      @Test
      @DisplayName("应该拒绝负数 limit")
      void shouldRejectNegativeLimit() {
        // Given
        Integer limit = -100;
        String unit = "RECORDS";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.VolumeBudget(limit, unit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("容量限制必须为正数");
      }

      @Test
      @DisplayName("应该拒绝 unit 为 null")
      void shouldRejectNullUnit() {
        // Given
        Integer limit = 100000;

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.VolumeBudget(limit, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("容量单位必须非空");
      }

      @Test
      @DisplayName("应该拒绝 unit 为空白字符串")
      void shouldRejectBlankUnit() {
        // Given
        Integer limit = 100000;
        String unit = "   ";

        // When & Then
        assertThatThrownBy(() -> new WindowSpec.VolumeBudget(limit, unit))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("容量单位必须非空");
      }

      @Test
      @DisplayName("应该接受不同的容量单位")
      void shouldAcceptDifferentUnits() {
        // Given & When
        WindowSpec.VolumeBudget window1 = new WindowSpec.VolumeBudget(1000, "RECORDS");
        WindowSpec.VolumeBudget window2 = new WindowSpec.VolumeBudget(1024, "BYTES");
        WindowSpec.VolumeBudget window3 = new WindowSpec.VolumeBudget(100, "MB");

        // Then
        assertThat(window1.unit()).isEqualTo("RECORDS");
        assertThat(window2.unit()).isEqualTo("BYTES");
        assertThat(window3.unit()).isEqualTo("MB");
      }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

      @Test
      @DisplayName("应该通过 ofVolume 工厂方法创建实例")
      void shouldCreateViaOfVolume() {
        // Given
        Integer limit = 100000;
        String unit = "RECORDS";

        // When
        WindowSpec.VolumeBudget window = WindowSpec.ofVolume(limit, unit);

        // Then
        assertThat(window.limit()).isEqualTo(limit);
        assertThat(window.unit()).isEqualTo(unit);
      }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTest {

      @Test
      @DisplayName("应该正确序列化为 Map 格式")
      void shouldSerializeToMapCorrectly() {
        // Given
        WindowSpec.VolumeBudget window = new WindowSpec.VolumeBudget(100000, "RECORDS");

        // When
        Map<String, Object> map = window.toMap();

        // Then
        assertThat(map)
            .containsEntry("strategy", "VOLUME_BUDGET")
            .containsEntry("limit", 100000)
            .containsEntry("unit", "RECORDS");
      }
    }

    @Nested
    @DisplayName("Record 语义测试")
    class RecordSemanticsTest {

      @Test
      @DisplayName("应该正确实现 equals")
      void shouldImplementEqualsCorrectly() {
        // Given
        WindowSpec.VolumeBudget window1 = new WindowSpec.VolumeBudget(100000, "RECORDS");
        WindowSpec.VolumeBudget window2 = new WindowSpec.VolumeBudget(100000, "RECORDS");
        WindowSpec.VolumeBudget window3 = new WindowSpec.VolumeBudget(50000, "RECORDS");

        // Then
        assertThat(window1).isEqualTo(window2);
        assertThat(window1).isNotEqualTo(window3);
      }

      @Test
      @DisplayName("应该正确实现 hashCode")
      void shouldImplementHashCodeCorrectly() {
        // Given
        WindowSpec.VolumeBudget window1 = new WindowSpec.VolumeBudget(100000, "RECORDS");
        WindowSpec.VolumeBudget window2 = new WindowSpec.VolumeBudget(100000, "RECORDS");

        // Then
        assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
      }

      @Test
      @DisplayName("应该正确实现 toString")
      void shouldImplementToStringCorrectly() {
        // Given
        WindowSpec.VolumeBudget window = new WindowSpec.VolumeBudget(100000, "RECORDS");

        // When
        String str = window.toString();

        // Then
        assertThat(str).contains("VolumeBudget").contains("100000").contains("RECORDS");
      }
    }
  }

  @Nested
  @DisplayName("Single 窗口规范测试")
  class SingleWindowTest {

    @Nested
    @DisplayName("构造器测试")
    class ConstructorTest {

      @Test
      @DisplayName("应该成功创建单一窗口")
      void shouldCreateSingleWindow() {
        // When
        WindowSpec.Single window = new WindowSpec.Single();

        // Then
        assertThat(window.strategy()).isEqualTo(SliceStrategy.SINGLE);
      }
    }

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTest {

      @Test
      @DisplayName("应该通过 ofSingle 工厂方法创建实例")
      void shouldCreateViaOfSingle() {
        // When
        WindowSpec.Single window = WindowSpec.ofSingle();

        // Then
        assertThat(window.strategy()).isEqualTo(SliceStrategy.SINGLE);
      }
    }

    @Nested
    @DisplayName("序列化测试")
    class SerializationTest {

      @Test
      @DisplayName("应该正确序列化为 Map 格式")
      void shouldSerializeToMapCorrectly() {
        // Given
        WindowSpec.Single window = new WindowSpec.Single();

        // When
        Map<String, Object> map = window.toMap();

        // Then
        assertThat(map).containsEntry("strategy", "SINGLE").hasSize(1);
      }
    }

    @Nested
    @DisplayName("Record 语义测试")
    class RecordSemanticsTest {

      @Test
      @DisplayName("应该正确实现 equals")
      void shouldImplementEqualsCorrectly() {
        // Given
        WindowSpec.Single window1 = new WindowSpec.Single();
        WindowSpec.Single window2 = new WindowSpec.Single();

        // Then
        assertThat(window1).isEqualTo(window2);
      }

      @Test
      @DisplayName("应该正确实现 hashCode")
      void shouldImplementHashCodeCorrectly() {
        // Given
        WindowSpec.Single window1 = new WindowSpec.Single();
        WindowSpec.Single window2 = new WindowSpec.Single();

        // Then
        assertThat(window1.hashCode()).isEqualTo(window2.hashCode());
      }

      @Test
      @DisplayName("应该正确实现 toString")
      void shouldImplementToStringCorrectly() {
        // Given
        WindowSpec.Single window = new WindowSpec.Single();

        // When
        String str = window.toString();

        // Then
        assertThat(str).contains("Single");
      }
    }
  }

  @Nested
  @DisplayName("fromMap 反序列化测试")
  class FromMapDeserializationTest {

    @Nested
    @DisplayName("TIME 策略反序列化测试")
    class TimeStrategyTest {

      @Test
      @DisplayName("应该成功从 Map 反序列化 TIME 窗口")
      void shouldDeserializeTimeWindow() {
        // Given
        Map<String, Object> windowMap =
            Map.of("from", "2024-01-01T00:00:00Z", "to", "2024-12-31T23:59:59Z");
        Map<String, Object> map = Map.of("strategy", "TIME", "window", windowMap);

        // When
        WindowSpec spec = WindowSpec.fromMap(map);

        // Then
        assertThat(spec).isInstanceOf(WindowSpec.Time.class);
        WindowSpec.Time time = (WindowSpec.Time) spec;
        assertThat(time.from()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(time.to()).isEqualTo(Instant.parse("2024-12-31T23:59:59Z"));
      }

      @Test
      @DisplayName("应该拒绝缺少 window 字段的 TIME 策略")
      void shouldRejectTimeWithoutWindow() {
        // Given
        Map<String, Object> map = Map.of("strategy", "TIME");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TIME 策略要求'window'对象存在");
      }

      @Test
      @DisplayName("应该拒绝 window 不是 Map 的 TIME 策略")
      void shouldRejectTimeWithInvalidWindow() {
        // Given
        Map<String, Object> map = Map.of("strategy", "TIME", "window", "invalid");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'window'必须是JSON对象");
      }

      @Test
      @DisplayName("应该拒绝缺少 from 字段的 TIME 窗口")
      void shouldRejectTimeWithoutFrom() {
        // Given
        Map<String, Object> windowMap = Map.of("to", "2024-12-31T23:59:59Z");
        Map<String, Object> map = Map.of("strategy", "TIME", "window", windowMap);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TIME 窗口要求'from'和'to'字段都存在");
      }

      @Test
      @DisplayName("应该拒绝 from 不是字符串的 TIME 窗口")
      void shouldRejectTimeWithInvalidFromType() {
        // Given
        Map<String, Object> windowMap = Map.of("from", 123456, "to", "2024-12-31T23:59:59Z");
        Map<String, Object> map = Map.of("strategy", "TIME", "window", windowMap);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("TIME 窗口'from'和'to'必须是ISO-8601时间戳字符串");
      }
    }

    @Nested
    @DisplayName("DATE 策略反序列化测试")
    class DateStrategyTest {

      @Test
      @DisplayName("应该成功从 Map 反序列化 DATE 窗口")
      void shouldDeserializeDateWindow() {
        // Given
        Map<String, Object> windowMap =
            Map.of("from", "2024-01-01T00:00:00Z", "to", "2024-12-31T23:59:59Z");
        Map<String, Object> map = Map.of("strategy", "DATE", "window", windowMap);

        // When
        WindowSpec spec = WindowSpec.fromMap(map);

        // Then
        assertThat(spec).isInstanceOf(WindowSpec.Time.class);
        WindowSpec.Time time = (WindowSpec.Time) spec;
        assertThat(time.from()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(time.to()).isEqualTo(Instant.parse("2024-12-31T23:59:59Z"));
      }
    }

    @Nested
    @DisplayName("ID_RANGE 策略反序列化测试")
    class IdRangeStrategyTest {

      @Test
      @DisplayName("应该成功从 Map 反序列化 ID_RANGE 窗口")
      void shouldDeserializeIdRangeWindow() {
        // Given
        Map<String, Object> windowMap = Map.of("from", 1000000, "to", 2000000);
        Map<String, Object> map = Map.of("strategy", "ID_RANGE", "window", windowMap);

        // When
        WindowSpec spec = WindowSpec.fromMap(map);

        // Then
        assertThat(spec).isInstanceOf(WindowSpec.IdRange.class);
        WindowSpec.IdRange idRange = (WindowSpec.IdRange) spec;
        assertThat(idRange.from()).isEqualTo(1000000L);
        assertThat(idRange.to()).isEqualTo(2000000L);
      }

      @Test
      @DisplayName("应该拒绝缺少 window 字段的 ID_RANGE 策略")
      void shouldRejectIdRangeWithoutWindow() {
        // Given
        Map<String, Object> map = Map.of("strategy", "ID_RANGE");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ID_RANGE 策略要求'window'对象存在");
      }

      @Test
      @DisplayName("应该拒绝缺少 from 字段的 ID_RANGE 窗口")
      void shouldRejectIdRangeWithoutFrom() {
        // Given
        Map<String, Object> windowMap = Map.of("to", 2000000);
        Map<String, Object> map = Map.of("strategy", "ID_RANGE", "window", windowMap);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ID_RANGE 窗口要求'from'和'to'字段都存在");
      }

      @Test
      @DisplayName("应该拒绝 from 不是数值的 ID_RANGE 窗口")
      void shouldRejectIdRangeWithInvalidFromType() {
        // Given
        Map<String, Object> windowMap = Map.of("from", "invalid", "to", 2000000);
        Map<String, Object> map = Map.of("strategy", "ID_RANGE", "window", windowMap);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ID_RANGE 窗口'from'和'to'必须是数值");
      }
    }

    @Nested
    @DisplayName("CURSOR_LANDMARK 策略反序列化测试")
    class CursorLandmarkStrategyTest {

      @Test
      @DisplayName("应该成功从 Map 反序列化 CURSOR_LANDMARK 窗口")
      void shouldDeserializeCursorLandmarkWindow() {
        // Given
        Map<String, Object> windowMap = Map.of("from", "cursor_token_1", "to", "cursor_token_2");
        Map<String, Object> map = Map.of("strategy", "CURSOR_LANDMARK", "window", windowMap);

        // When
        WindowSpec spec = WindowSpec.fromMap(map);

        // Then
        assertThat(spec).isInstanceOf(WindowSpec.CursorLandmark.class);
        WindowSpec.CursorLandmark cursor = (WindowSpec.CursorLandmark) spec;
        assertThat(cursor.from()).isEqualTo("cursor_token_1");
        assertThat(cursor.to()).isEqualTo("cursor_token_2");
      }

      @Test
      @DisplayName("应该拒绝缺少 window 字段的 CURSOR_LANDMARK 策略")
      void shouldRejectCursorLandmarkWithoutWindow() {
        // Given
        Map<String, Object> map = Map.of("strategy", "CURSOR_LANDMARK");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CURSOR_LANDMARK 策略要求'window'对象存在");
      }

      @Test
      @DisplayName("应该拒绝缺少 to 字段的 CURSOR_LANDMARK 窗口")
      void shouldRejectCursorLandmarkWithoutTo() {
        // Given
        Map<String, Object> windowMap = Map.of("from", "cursor_token_1");
        Map<String, Object> map = Map.of("strategy", "CURSOR_LANDMARK", "window", windowMap);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CURSOR_LANDMARK 窗口要求'from'和'to'字段都存在");
      }

      @Test
      @DisplayName("应该拒绝 from 不是字符串的 CURSOR_LANDMARK 窗口")
      void shouldRejectCursorLandmarkWithInvalidFromType() {
        // Given
        Map<String, Object> windowMap = Map.of("from", 123, "to", "cursor_token_2");
        Map<String, Object> map = Map.of("strategy", "CURSOR_LANDMARK", "window", windowMap);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CURSOR_LANDMARK 窗口'from'和'to'必须是字符串值");
      }
    }

    @Nested
    @DisplayName("VOLUME_BUDGET 策略反序列化测试")
    class VolumeBudgetStrategyTest {

      @Test
      @DisplayName("应该成功从 Map 反序列化 VOLUME_BUDGET 窗口")
      void shouldDeserializeVolumeBudgetWindow() {
        // Given
        Map<String, Object> map =
            Map.of("strategy", "VOLUME_BUDGET", "limit", 100000, "unit", "RECORDS");

        // When
        WindowSpec spec = WindowSpec.fromMap(map);

        // Then
        assertThat(spec).isInstanceOf(WindowSpec.VolumeBudget.class);
        WindowSpec.VolumeBudget volume = (WindowSpec.VolumeBudget) spec;
        assertThat(volume.limit()).isEqualTo(100000);
        assertThat(volume.unit()).isEqualTo("RECORDS");
      }

      @Test
      @DisplayName("应该拒绝缺少 limit 字段的 VOLUME_BUDGET 策略")
      void shouldRejectVolumeBudgetWithoutLimit() {
        // Given
        Map<String, Object> map = Map.of("strategy", "VOLUME_BUDGET", "unit", "RECORDS");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("VOLUME_BUDGET 策略要求'limit'和'unit'字段都存在");
      }

      @Test
      @DisplayName("应该拒绝 limit 不是数值的 VOLUME_BUDGET 策略")
      void shouldRejectVolumeBudgetWithInvalidLimitType() {
        // Given
        Map<String, Object> map =
            Map.of("strategy", "VOLUME_BUDGET", "limit", "invalid", "unit", "RECORDS");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("VOLUME_BUDGET 'limit'必须是数值");
      }

      @Test
      @DisplayName("应该拒绝 unit 不是字符串的 VOLUME_BUDGET 策略")
      void shouldRejectVolumeBudgetWithInvalidUnitType() {
        // Given
        Map<String, Object> map = Map.of("strategy", "VOLUME_BUDGET", "limit", 100000, "unit", 123);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("VOLUME_BUDGET 'unit'必须是字符串值");
      }
    }

    @Nested
    @DisplayName("SINGLE 策略反序列化测试")
    class SingleStrategyTest {

      @Test
      @DisplayName("应该成功从 Map 反序列化 SINGLE 窗口")
      void shouldDeserializeSingleWindow() {
        // Given
        Map<String, Object> map = Map.of("strategy", "SINGLE");

        // When
        WindowSpec spec = WindowSpec.fromMap(map);

        // Then
        assertThat(spec).isInstanceOf(WindowSpec.Single.class);
      }
    }

    @Nested
    @DisplayName("通用验证测试")
    class GeneralValidationTest {

      @Test
      @DisplayName("应该拒绝 null map")
      void shouldRejectNullMap() {
        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("窗口规范map不能为null或空");
      }

      @Test
      @DisplayName("应该拒绝空 map")
      void shouldRejectEmptyMap() {
        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("窗口规范map不能为null或空");
      }

      @Test
      @DisplayName("应该拒绝缺少 strategy 字段的 map")
      void shouldRejectMapWithoutStrategy() {
        // Given
        Map<String, Object> map = Map.of("other", "value");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("窗口规范map必须包含'strategy'键");
      }

      @Test
      @DisplayName("应该拒绝 strategy 不是字符串的 map")
      void shouldRejectMapWithInvalidStrategyType() {
        // Given
        Map<String, Object> map = Map.of("strategy", 123);

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("'strategy'必须是字符串");
      }

      @Test
      @DisplayName("应该拒绝未知的策略代码")
      void shouldRejectUnknownStrategyCode() {
        // Given
        Map<String, Object> map = Map.of("strategy", "UNKNOWN_STRATEGY");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未知的切片策略代码: 'UNKNOWN_STRATEGY'");
      }

      @Test
      @DisplayName("应该拒绝 HYBRID 策略（尚未实现）")
      void shouldRejectHybridStrategy() {
        // Given
        Map<String, Object> map = Map.of("strategy", "HYBRID");

        // When & Then
        assertThatThrownBy(() -> WindowSpec.fromMap(map))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("HYBRID策略尚未实现");
      }

      @Test
      @DisplayName("应该忽略策略代码的大小写")
      void shouldIgnoreStrategyCaseInsensitivity() {
        // Given
        Map<String, Object> map1 = Map.of("strategy", "single");
        Map<String, Object> map2 = Map.of("strategy", "Single");
        Map<String, Object> map3 = Map.of("strategy", "SINGLE");

        // When
        WindowSpec spec1 = WindowSpec.fromMap(map1);
        WindowSpec spec2 = WindowSpec.fromMap(map2);
        WindowSpec spec3 = WindowSpec.fromMap(map3);

        // Then
        assertThat(spec1).isInstanceOf(WindowSpec.Single.class);
        assertThat(spec2).isInstanceOf(WindowSpec.Single.class);
        assertThat(spec3).isInstanceOf(WindowSpec.Single.class);
      }
    }
  }

  @Nested
  @DisplayName("序列化-反序列化往返测试")
  class SerializationRoundTripTest {

    @Test
    @DisplayName("Time 窗口应该支持序列化-反序列化往返")
    void shouldSupportTimeWindowRoundTrip() {
      // Given
      WindowSpec.Time original =
          new WindowSpec.Time(
              Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-12-31T23:59:59Z"));

      // When
      Map<String, Object> map = original.toMap();
      WindowSpec deserialized = WindowSpec.fromMap(map);

      // Then
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    @DisplayName("IdRange 窗口应该支持序列化-反序列化往返")
    void shouldSupportIdRangeWindowRoundTrip() {
      // Given
      WindowSpec.IdRange original = new WindowSpec.IdRange(1000000L, 2000000L);

      // When
      Map<String, Object> map = original.toMap();
      WindowSpec deserialized = WindowSpec.fromMap(map);

      // Then
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    @DisplayName("CursorLandmark 窗口应该支持序列化-反序列化往返")
    void shouldSupportCursorLandmarkWindowRoundTrip() {
      // Given
      WindowSpec.CursorLandmark original =
          new WindowSpec.CursorLandmark("cursor_token_1", "cursor_token_2");

      // When
      Map<String, Object> map = original.toMap();
      WindowSpec deserialized = WindowSpec.fromMap(map);

      // Then
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    @DisplayName("VolumeBudget 窗口应该支持序列化-反序列化往返")
    void shouldSupportVolumeBudgetWindowRoundTrip() {
      // Given
      WindowSpec.VolumeBudget original = new WindowSpec.VolumeBudget(100000, "RECORDS");

      // When
      Map<String, Object> map = original.toMap();
      WindowSpec deserialized = WindowSpec.fromMap(map);

      // Then
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    @DisplayName("Single 窗口应该支持序列化-反序列化往返")
    void shouldSupportSingleWindowRoundTrip() {
      // Given
      WindowSpec.Single original = new WindowSpec.Single();

      // When
      Map<String, Object> map = original.toMap();
      WindowSpec deserialized = WindowSpec.fromMap(map);

      // Then
      assertThat(deserialized).isEqualTo(original);
    }
  }
}
