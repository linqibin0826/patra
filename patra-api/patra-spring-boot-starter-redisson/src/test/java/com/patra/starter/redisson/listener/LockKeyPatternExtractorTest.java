package com.patra.starter.redisson.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/// `LockKeyPatternExtractor` 单元测试。
///
/// 测试锁键模式提取的各种场景，包括：
/// - 前缀移除
/// - 动态部分过滤（纯数字、UUID、日期）
/// - 静态部分保留
/// - 边界情况处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("LockKeyPatternExtractor 锁键模式提取测试")
class LockKeyPatternExtractorTest {

  @Nested
  @DisplayName("正常场景")
  class NormalScenarios {

    @ParameterizedTest(name = "锁键 [{0}] 应提取为 [{1}]")
    @CsvSource({
      "patra:lock:user:123, user",
      "patra:lock:order:456:item:789, order.item",
      "catalog:lock:mesh-import:2024, mesh-import",
      "ingest:lock:task:ready:1001, task.ready",
      "patra:lock:publication:sync, publication.sync"
    })
    @DisplayName("应正确提取带前缀的锁键模式")
    void shouldExtractPatternWithPrefix(String lockKey, String expected) {
      assertThat(LockKeyPatternExtractor.extract(lockKey)).isEqualTo(expected);
    }

    @Test
    @DisplayName("应处理无前缀的锁键")
    void shouldHandleKeyWithoutPrefix() {
      assertThat(LockKeyPatternExtractor.extract("user:123")).isEqualTo("user");
      assertThat(LockKeyPatternExtractor.extract("order:item:789")).isEqualTo("order.item");
    }

    @Test
    @DisplayName("应合并多个静态部分为点分隔模式")
    void shouldMergeMultipleStaticParts() {
      assertThat(LockKeyPatternExtractor.extract("patra:lock:module:sub:action"))
          .isEqualTo("module.sub.action");
    }
  }

  @Nested
  @DisplayName("动态部分过滤")
  class DynamicPartFiltering {

    @ParameterizedTest(name = "应过滤纯数字 [{0}]")
    @CsvSource({
      "patra:lock:user:1, user",
      "patra:lock:user:123456789, user",
      "patra:lock:order:0:item:0, order.item"
    })
    @DisplayName("应过滤纯数字部分")
    void shouldFilterNumericParts(String lockKey, String expected) {
      assertThat(LockKeyPatternExtractor.extract(lockKey)).isEqualTo(expected);
    }

    @Test
    @DisplayName("应过滤 UUID 格式")
    void shouldFilterUuidParts() {
      String uuid = "550e8400-e29b-41d4-a716-446655440000";
      assertThat(LockKeyPatternExtractor.extract("patra:lock:session:" + uuid)).isEqualTo("session");
      assertThat(LockKeyPatternExtractor.extract("patra:lock:user:" + uuid + ":profile"))
          .isEqualTo("user.profile");
    }

    @ParameterizedTest(name = "应过滤日期格式 [{0}]")
    @CsvSource({
      "patra:lock:report:2024, report",
      "patra:lock:report:2024-01, report",
      "patra:lock:report:2024-01-15, report",
      "patra:lock:report:20240115, report",
      "patra:lock:daily:2024-12-31:task, daily.task"
    })
    @DisplayName("应过滤日期格式部分")
    void shouldFilterDateParts(String lockKey, String expected) {
      assertThat(LockKeyPatternExtractor.extract(lockKey)).isEqualTo(expected);
    }

    @Test
    @DisplayName("应保留包含字母的混合部分")
    void shouldKeepMixedAlphanumericParts() {
      // task-001 包含字母，不是纯数字，应保留
      assertThat(LockKeyPatternExtractor.extract("patra:lock:task-001")).isEqualTo("task-001");
      assertThat(LockKeyPatternExtractor.extract("patra:lock:v2:api")).isEqualTo("v2.api");
    }
  }

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("空或 null 输入应返回 unknown")
    void shouldReturnUnknownForNullOrEmpty(String lockKey) {
      assertThat(LockKeyPatternExtractor.extract(lockKey)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("仅包含动态部分应返回 unknown")
    void shouldReturnUnknownForOnlyDynamicParts() {
      assertThat(LockKeyPatternExtractor.extract("patra:lock:123:456:789")).isEqualTo("unknown");
      assertThat(
              LockKeyPatternExtractor.extract(
                  "patra:lock:550e8400-e29b-41d4-a716-446655440000"))
          .isEqualTo("unknown");
    }

    @Test
    @DisplayName("应处理连续冒号")
    void shouldHandleConsecutiveColons() {
      assertThat(LockKeyPatternExtractor.extract("patra:lock:user::action")).isEqualTo("user.action");
    }

    @Test
    @DisplayName("应处理尾部冒号")
    void shouldHandleTrailingColon() {
      assertThat(LockKeyPatternExtractor.extract("patra:lock:user:")).isEqualTo("user");
    }
  }

  @Nested
  @DisplayName("前缀移除")
  class PrefixRemoval {

    @ParameterizedTest(name = "前缀 [{0}] 应被移除")
    @CsvSource({
      "patra:lock:user, user",
      "catalog:lock:mesh, mesh",
      "ingest:lock:task, task",
      "registry:lock:config, config",
      "my-service:lock:resource, resource"
    })
    @DisplayName("应移除各种服务前缀")
    void shouldRemoveVariousServicePrefixes(String lockKey, String expected) {
      assertThat(LockKeyPatternExtractor.extract(lockKey)).isEqualTo(expected);
    }

    @Test
    @DisplayName("不匹配的前缀模式应保留")
    void shouldKeepNonMatchingPrefix() {
      // "PATRA:lock:" 大写不匹配 [a-z-]+:lock: 模式
      assertThat(LockKeyPatternExtractor.extract("PATRA:lock:user")).isEqualTo("PATRA.lock.user");
      // "patra_lock:" 使用下划线，不匹配
      assertThat(LockKeyPatternExtractor.extract("patra_lock:user")).isEqualTo("patra_lock.user");
    }
  }
}
