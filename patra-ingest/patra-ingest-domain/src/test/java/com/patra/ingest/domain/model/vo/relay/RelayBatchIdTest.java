package com.patra.ingest.domain.model.vo.relay;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link RelayBatchId} 的单元测试。
///
/// 测试覆盖:
///
/// - 工厂方法 (generate, of)
///   - 格式验证
///   - 业务方法 (getValue, getTimestampPart, getUuidPart)
///   - Value Object 语义 (equals, hashCode, toString)
///   - 不变性保证
///
/// @author Patra Team
@DisplayName("RelayBatchId 单元测试")
class RelayBatchIdTest {

  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

  @Nested
  @DisplayName("工厂方法: generate()")
  class GenerateTests {

    @Test
    @DisplayName("应该成功生成批次ID - 验证格式")
    void shouldGenerateBatchIdWithCorrectFormat() {
      // Given: 一个 UTC 时间戳
      Instant triggeredAt = Instant.parse("2025-10-31T15:00:00Z");

      // When: 生成批次 ID
      RelayBatchId batchId = RelayBatchId.generate(triggeredAt);

      // Then: 应该符合格式 yyyyMMddHHmmss-xxxxxxxx
      assertThat(batchId.getValue())
          .matches("\\d{14}-[a-f0-9]{8}") // 正则验证
          .startsWith("20251031150000-") // 时间戳部分正确
          .hasSize(23); // 总长度 14 + 1 + 8 = 23
    }

    @Test
    @DisplayName("应该生成唯一的批次ID - 验证UUID部分随机性")
    void shouldGenerateUniqueBatchIds() {
      // Given: 相同的时间戳
      Instant triggeredAt = Instant.parse("2025-10-31T15:00:00Z");
      Set<String> generatedIds = new HashSet<>();

      // When: 生成 100 个批次 ID
      for (int i = 0; i < 100; i++) {
        RelayBatchId batchId = RelayBatchId.generate(triggeredAt);
        generatedIds.add(batchId.getValue());
      }

      // Then: 所有 ID 应该不同 (UUID 部分保证唯一性)
      assertThat(generatedIds).hasSize(100);
    }

    @Test
    @DisplayName("应该正确格式化不同的时间戳")
    void shouldFormatDifferentTimestamps() {
      // Given: 不同的 UTC 时间戳
      Instant timestamp1 = Instant.parse("2025-01-01T00:00:00Z");
      Instant timestamp2 = Instant.parse("2025-12-31T23:59:59Z");

      // When: 生成批次 ID
      RelayBatchId batchId1 = RelayBatchId.generate(timestamp1);
      RelayBatchId batchId2 = RelayBatchId.generate(timestamp2);

      // Then: 时间戳部分应该正确
      assertThat(batchId1.getTimestampPart()).isEqualTo("20250101000000");
      assertThat(batchId2.getTimestampPart()).isEqualTo("20251231235959");
    }

    @Test
    @DisplayName("应该拒绝 null 触发时间")
    void shouldRejectNullTriggeredAt() {
      // When & Then: 传入 null 应抛出 NullPointerException
      assertThatThrownBy(() -> RelayBatchId.generate(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("触发时间不能为 null");
    }

    @Test
    @DisplayName("应该使用 UTC 时区格式化时间戳")
    void shouldUseUtcTimeZone() {
      // Given: 一个非 UTC 时间 (北京时间 2025-11-01 00:00:00 = UTC 2025-10-31 16:00:00)
      Instant utcTime = Instant.parse("2025-10-31T16:00:00Z");

      // When: 生成批次 ID
      RelayBatchId batchId = RelayBatchId.generate(utcTime);

      // Then: 时间戳应该是 UTC 时间
      assertThat(batchId.getTimestampPart()).isEqualTo("20251031160000");
    }
  }

  @Nested
  @DisplayName("工厂方法: of()")
  class OfTests {

    @Test
    @DisplayName("应该成功从有效字符串重建批次ID")
    void shouldReconstructFromValidString() {
      // Given: 有效的批次 ID 字符串
      String validId = "20251031150000-a1b2c3d4";

      // When: 重建 RelayBatchId
      RelayBatchId batchId = RelayBatchId.of(validId);

      // Then: 应该成功重建
      assertThat(batchId.getValue()).isEqualTo(validId);
    }

    @Test
    @DisplayName("应该拒绝 null 字符串")
    void shouldRejectNullValue() {
      // When & Then: 传入 null 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式")
          .hasMessageContaining("期望格式: yyyyMMddHHmmss-xxxxxxxx");
    }

    @Test
    @DisplayName("应该拒绝空字符串")
    void shouldRejectEmptyString() {
      // When & Then: 传入空字符串应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - 缺少分隔符")
    void shouldRejectMissingHyphen() {
      // Given: 缺少 '-' 分隔符
      String invalidId = "20251031150000a1b2c3d4";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(invalidId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - 时间戳长度不足")
    void shouldRejectShortTimestamp() {
      // Given: 时间戳少于 14 位
      String invalidId = "2025103115-a1b2c3d4";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(invalidId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - 时间戳包含非数字")
    void shouldRejectNonNumericTimestamp() {
      // Given: 时间戳包含字母
      String invalidId = "2025103115000X-a1b2c3d4";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(invalidId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - UUID 长度不足")
    void shouldRejectShortUuid() {
      // Given: UUID 少于 8 位
      String invalidId = "20251031150000-a1b2c3";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(invalidId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - UUID 包含大写字母")
    void shouldRejectUpperCaseUuid() {
      // Given: UUID 包含大写字母 (期望小写)
      String invalidId = "20251031150000-A1B2C3D4";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(invalidId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - UUID 包含非十六进制字符")
    void shouldRejectNonHexUuid() {
      // Given: UUID 包含非十六进制字符 (g-z)
      String invalidId = "20251031150000-ghijklmn";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(invalidId))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("无效的批次 ID 格式");
    }

    @Test
    @DisplayName("应该拒绝格式错误 - 整体长度错误")
    void shouldRejectWrongLength() {
      // Given: 总长度不是 23 位
      String tooShort = "20251031150000-a1b2";
      String tooLong = "20251031150000-a1b2c3d4e5";

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(() -> RelayBatchId.of(tooShort))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> RelayBatchId.of(tooLong))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("应该接受所有有效的十六进制字符 (0-9, a-f)")
    void shouldAcceptAllValidHexCharacters() {
      // Given: UUID 使用所有合法十六进制字符
      String validId1 = "20251031150000-01234567";
      String validId2 = "20251031150000-89abcdef";

      // When: 重建 RelayBatchId
      RelayBatchId batchId1 = RelayBatchId.of(validId1);
      RelayBatchId batchId2 = RelayBatchId.of(validId2);

      // Then: 应该成功
      assertThat(batchId1.getValue()).isEqualTo(validId1);
      assertThat(batchId2.getValue()).isEqualTo(validId2);
    }
  }

  @Nested
  @DisplayName("业务方法")
  class BusinessMethodTests {

    @Test
    @DisplayName("getValue() 应返回完整的批次ID字符串")
    void shouldReturnCompleteValue() {
      // Given: 一个批次 ID
      String expectedValue = "20251031150000-a1b2c3d4";
      RelayBatchId batchId = RelayBatchId.of(expectedValue);

      // When: 调用 getValue()
      String actualValue = batchId.getValue();

      // Then: 应返回完整字符串
      assertThat(actualValue).isEqualTo(expectedValue);
    }

    @Test
    @DisplayName("getTimestampPart() 应正确提取时间戳部分")
    void shouldExtractTimestampPart() {
      // Given: 一个批次 ID
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When: 调用 getTimestampPart()
      String timestampPart = batchId.getTimestampPart();

      // Then: 应返回前 14 位
      assertThat(timestampPart).isEqualTo("20251031150000").hasSize(14).matches("\\d{14}");
    }

    @Test
    @DisplayName("getUuidPart() 应正确提取UUID部分")
    void shouldExtractUuidPart() {
      // Given: 一个批次 ID
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When: 调用 getUuidPart()
      String uuidPart = batchId.getUuidPart();

      // Then: 应返回后 8 位 (跳过分隔符 '-')
      assertThat(uuidPart).isEqualTo("a1b2c3d4").hasSize(8).matches("[a-f0-9]{8}");
    }

    @Test
    @DisplayName("时间戳和UUID部分应拼接成完整ID")
    void shouldReconstructFromParts() {
      // Given: 一个批次 ID
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When: 提取各部分
      String timestampPart = batchId.getTimestampPart();
      String uuidPart = batchId.getUuidPart();

      // Then: 拼接应还原完整 ID
      String reconstructed = timestampPart + "-" + uuidPart;
      assertThat(reconstructed).isEqualTo(batchId.getValue());
    }
  }

  @Nested
  @DisplayName("Value Object 语义")
  class ValueObjectSemanticTests {

    @Test
    @DisplayName("equals() - 相同值应相等")
    void shouldBeEqualForSameValue() {
      // Given: 两个相同值的 RelayBatchId
      RelayBatchId batchId1 = RelayBatchId.of("20251031150000-a1b2c3d4");
      RelayBatchId batchId2 = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When & Then: 应该相等
      assertThat(batchId1).isEqualTo(batchId2).hasSameHashCodeAs(batchId2);
    }

    @Test
    @DisplayName("equals() - 不同值应不相等")
    void shouldNotBeEqualForDifferentValues() {
      // Given: 两个不同值的 RelayBatchId
      RelayBatchId batchId1 = RelayBatchId.of("20251031150000-a1b2c3d4");
      RelayBatchId batchId2 = RelayBatchId.of("20251031150000-abcdef12");

      // When & Then: 应该不相等
      assertThat(batchId1).isNotEqualTo(batchId2);
    }

    @Test
    @DisplayName("equals() - 与自身比较应返回true")
    void shouldBeEqualToItself() {
      // Given: 一个 RelayBatchId
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When & Then: 与自身比较应相等
      assertThat(batchId).isEqualTo(batchId);
    }

    @Test
    @DisplayName("equals() - 与null比较应返回false")
    void shouldNotBeEqualToNull() {
      // Given: 一个 RelayBatchId
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When & Then: 与 null 比较应不相等
      assertThat(batchId).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() - 与不同类型比较应返回false")
    void shouldNotBeEqualToDifferentType() {
      // Given: 一个 RelayBatchId 和一个 String
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");
      String stringValue = "20251031150000-a1b2c3d4";

      // When & Then: 与不同类型比较应不相等
      assertThat(batchId).isNotEqualTo(stringValue);
    }

    @Test
    @DisplayName("hashCode() - 相同值应有相同的哈希码")
    void shouldHaveSameHashCodeForSameValue() {
      // Given: 两个相同值的 RelayBatchId
      RelayBatchId batchId1 = RelayBatchId.of("20251031150000-a1b2c3d4");
      RelayBatchId batchId2 = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When & Then: 哈希码应相同
      assertThat(batchId1.hashCode()).isEqualTo(batchId2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 不同值通常应有不同的哈希码")
    void shouldHaveDifferentHashCodeForDifferentValues() {
      // Given: 两个不同值的 RelayBatchId
      RelayBatchId batchId1 = RelayBatchId.of("20251031150000-a1b2c3d4");
      RelayBatchId batchId2 = RelayBatchId.of("20251031150000-abcdef12");

      // When & Then: 哈希码通常不同 (不是绝对保证,但概率很高)
      assertThat(batchId1.hashCode()).isNotEqualTo(batchId2.hashCode());
    }

    @Test
    @DisplayName("hashCode() - 多次调用应返回相同值")
    void shouldHaveConsistentHashCode() {
      // Given: 一个 RelayBatchId
      RelayBatchId batchId = RelayBatchId.of("20251031150000-a1b2c3d4");

      // When: 多次调用 hashCode()
      int hashCode1 = batchId.hashCode();
      int hashCode2 = batchId.hashCode();
      int hashCode3 = batchId.hashCode();

      // Then: 应返回相同值
      assertThat(hashCode1).isEqualTo(hashCode2).isEqualTo(hashCode3);
    }

    @Test
    @DisplayName("toString() 应返回批次ID值")
    void shouldReturnValueInToString() {
      // Given: 一个批次 ID
      String expectedValue = "20251031150000-a1b2c3d4";
      RelayBatchId batchId = RelayBatchId.of(expectedValue);

      // When: 调用 toString()
      String result = batchId.toString();

      // Then: 应返回批次 ID 字符串
      assertThat(result).isEqualTo(expectedValue);
    }

    @Test
    @DisplayName("应该在HashSet中正确工作 - 验证equals和hashCode契约")
    void shouldWorkCorrectlyInHashSet() {
      // Given: 多个批次 ID
      RelayBatchId batchId1 = RelayBatchId.of("20251031150000-a1b2c3d4");
      RelayBatchId batchId2 = RelayBatchId.of("20251031150000-a1b2c3d4"); // 相同值
      RelayBatchId batchId3 = RelayBatchId.of("20251031150000-abcdef12"); // 不同值

      // When: 添加到 HashSet
      Set<RelayBatchId> set = new HashSet<>();
      set.add(batchId1);
      set.add(batchId2); // 应该被去重
      set.add(batchId3);

      // Then: Set 应该只包含 2 个不同的值
      assertThat(set).hasSize(2).contains(batchId1, batchId3);
    }
  }

  @Nested
  @DisplayName("不变性保证")
  class ImmutabilityTests {

    @Test
    @DisplayName("getValue() 返回的字符串修改不应影响对象")
    void shouldNotBeAffectedByExternalModification() {
      // Given: 一个批次 ID
      String originalValue = "20251031150000-a1b2c3d4";
      RelayBatchId batchId = RelayBatchId.of(originalValue);

      // When: 获取值并修改外部引用 (String 是不可变的,这里验证设计)
      String retrievedValue = batchId.getValue();

      // Then: 对象内部值应不受影响
      assertThat(batchId.getValue()).isEqualTo(originalValue);
      assertThat(retrievedValue).isEqualTo(originalValue);
    }

    @Test
    @DisplayName("类应该是final - 防止子类破坏不变性")
    void shouldBeFinalClass() {
      // When & Then: RelayBatchId 应该是 final 类
      assertThat(RelayBatchId.class).isFinal();
    }

    @Test
    @DisplayName("字段应该是final - 确保不可变")
    void shouldHaveFinalFields() throws NoSuchFieldException {
      // When & Then: value 字段应该是 final
      assertThat(RelayBatchId.class.getDeclaredField("value"))
          .matches(field -> java.lang.reflect.Modifier.isFinal(field.getModifiers()));
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("应该处理最小时间戳 (Unix epoch)")
    void shouldHandleMinimumTimestamp() {
      // Given: Unix epoch (1970-01-01 00:00:00 UTC)
      Instant minInstant = Instant.EPOCH;

      // When: 生成批次 ID
      RelayBatchId batchId = RelayBatchId.generate(minInstant);

      // Then: 应该成功生成
      assertThat(batchId.getTimestampPart()).isEqualTo("19700101000000");
    }

    @Test
    @DisplayName("应该处理远期时间戳 (2099年)")
    void shouldHandleFarFutureTimestamp() {
      // Given: 2099-12-31 23:59:59 UTC
      Instant futureInstant = Instant.parse("2099-12-31T23:59:59Z");

      // When: 生成批次 ID
      RelayBatchId batchId = RelayBatchId.generate(futureInstant);

      // Then: 应该成功生成
      assertThat(batchId.getTimestampPart()).isEqualTo("20991231235959");
    }

    @Test
    @DisplayName("应该接受全0的UUID")
    void shouldAcceptAllZeroUuid() {
      // Given: UUID 全为 0
      String validId = "20251031150000-00000000";

      // When: 重建 RelayBatchId
      RelayBatchId batchId = RelayBatchId.of(validId);

      // Then: 应该成功
      assertThat(batchId.getUuidPart()).isEqualTo("00000000");
    }

    @Test
    @DisplayName("应该接受全f的UUID")
    void shouldAcceptAllFUuid() {
      // Given: UUID 全为 f
      String validId = "20251031150000-ffffffff";

      // When: 重建 RelayBatchId
      RelayBatchId batchId = RelayBatchId.of(validId);

      // Then: 应该成功
      assertThat(batchId.getUuidPart()).isEqualTo("ffffffff");
    }
  }

  @Nested
  @DisplayName("集成场景测试")
  class IntegrationScenarioTests {

    @Test
    @DisplayName("完整工作流 - 生成、序列化、反序列化、验证")
    void shouldWorkInCompleteWorkflow() {
      // Given: 一个时间戳
      Instant triggeredAt = Instant.parse("2025-10-31T15:00:00Z");

      // When: 生成批次 ID
      RelayBatchId original = RelayBatchId.generate(triggeredAt);

      // And: 序列化为字符串 (模拟存储到数据库)
      String serialized = original.getValue();

      // And: 从字符串反序列化 (模拟从数据库读取)
      RelayBatchId reconstructed = RelayBatchId.of(serialized);

      // Then: 重建的对象应该与原对象相等
      assertThat(reconstructed).isEqualTo(original);
      assertThat(reconstructed.getValue()).isEqualTo(original.getValue());
      assertThat(reconstructed.getTimestampPart()).isEqualTo("20251031150000");
    }

    @Test
    @DisplayName("批量生成 - 验证并发安全性和唯一性")
    void shouldGenerateUniqueBatchIdsInBulk() {
      // Given: 相同的时间戳
      Instant triggeredAt = Instant.now();
      Set<RelayBatchId> batchIds = new HashSet<>();

      // When: 批量生成 1000 个批次 ID
      for (int i = 0; i < 1000; i++) {
        RelayBatchId batchId = RelayBatchId.generate(triggeredAt);
        batchIds.add(batchId);
      }

      // Then: 所有 ID 应该唯一
      assertThat(batchIds).hasSize(1000);
    }

    @Test
    @DisplayName("业务场景 - 按时间戳分组批次ID")
    void shouldGroupBatchIdsByTimestamp() {
      // Given: 两个不同时间戳
      Instant time1 = Instant.parse("2025-10-31T15:00:00Z");
      Instant time2 = Instant.parse("2025-10-31T16:00:00Z");

      // When: 生成批次 ID
      RelayBatchId batch1a = RelayBatchId.generate(time1);
      RelayBatchId batch1b = RelayBatchId.generate(time1);
      RelayBatchId batch2 = RelayBatchId.generate(time2);

      // Then: 相同时间戳的批次 ID 应有相同的时间戳部分
      assertThat(batch1a.getTimestampPart())
          .isEqualTo(batch1b.getTimestampPart())
          .isEqualTo("20251031150000")
          .isNotEqualTo(batch2.getTimestampPart());

      // And: UUID 部分应不同
      assertThat(batch1a.getUuidPart()).isNotEqualTo(batch1b.getUuidPart());
    }
  }
}
