package dev.linqibin.patra.ingest.domain.model.vo.relay;

import static org.assertj.core.api.Assertions.*;

import dev.linqibin.commons.messaging.ChannelKey;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("RelayPlan 值对象测试")
class RelayPlanTest {

  @Nested
  @DisplayName("构造验证测试")
  class ConstructorValidationTests {

    @Test
    @DisplayName("应该成功创建有效的 RelayPlan")
    void shouldCreateValidRelayPlan() {
      // Given: 准备有效的参数
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      int batchSize = 100;
      Duration leaseDuration = Duration.ofMinutes(5);
      int maxAttempts = 3;
      Duration initialBackoff = Duration.ofSeconds(1);
      double backoffMultiplier = 2.0;
      Duration maxBackoff = Duration.ofMinutes(10);
      String leaseOwner = "worker-01";

      // When: 创建 RelayPlan
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              batchSize,
              leaseDuration,
              maxAttempts,
              initialBackoff,
              backoffMultiplier,
              maxBackoff,
              leaseOwner);

      // Then: 所有字段应正确赋值
      assertThat(plan.channel()).isEqualTo(channel);
      assertThat(plan.triggeredAt()).isEqualTo(triggeredAt);
      assertThat(plan.batchSize()).isEqualTo(batchSize);
      assertThat(plan.leaseDuration()).isEqualTo(leaseDuration);
      assertThat(plan.maxAttempts()).isEqualTo(maxAttempts);
      assertThat(plan.initialBackoff()).isEqualTo(initialBackoff);
      assertThat(plan.backoffMultiplier()).isEqualTo(backoffMultiplier);
      assertThat(plan.maxBackoff()).isEqualTo(maxBackoff);
      assertThat(plan.leaseOwner()).isEqualTo(leaseOwner);
    }

    @Test
    @DisplayName("应该允许 channel 为 null（表示所有通道）")
    void shouldAllowNullChannel() {
      // Given: channel 为 null
      ChannelKey channel = null;
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      int batchSize = 50;
      Duration leaseDuration = Duration.ofMinutes(3);
      int maxAttempts = 5;
      Duration initialBackoff = Duration.ofSeconds(2);
      double backoffMultiplier = 1.5;
      Duration maxBackoff = Duration.ofMinutes(5);
      String leaseOwner = "worker-02";

      // When: 创建 RelayPlan
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              batchSize,
              leaseDuration,
              maxAttempts,
              initialBackoff,
              backoffMultiplier,
              maxBackoff,
              leaseOwner);

      // Then: channel 应为 null
      assertThat(plan.channel()).isNull();
    }

    @Test
    @DisplayName("应该拒绝 triggeredAt 为 null")
    void shouldRejectNullTriggeredAt() {
      // Given: triggeredAt 为 null
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = null;

      // When & Then: 应抛出 NullPointerException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      triggeredAt,
                      100,
                      Duration.ofMinutes(5),
                      3,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("triggeredAt must not be null");
    }

    @Test
    @DisplayName("应该拒绝 leaseDuration 为 null")
    void shouldRejectNullLeaseDuration() {
      // Given: leaseDuration 为 null
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Duration leaseDuration = null;

      // When & Then: 应抛出 NullPointerException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      leaseDuration,
                      3,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("leaseDuration must not be null");
    }

    @Test
    @DisplayName("应该拒绝 initialBackoff 为 null")
    void shouldRejectNullInitialBackoff() {
      // Given: initialBackoff 为 null
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Duration initialBackoff = null;

      // When & Then: 应抛出 NullPointerException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      Duration.ofMinutes(5),
                      3,
                      initialBackoff,
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("initialBackoff must not be null");
    }

    @Test
    @DisplayName("应该拒绝 maxBackoff 为 null")
    void shouldRejectNullMaxBackoff() {
      // Given: maxBackoff 为 null
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Duration maxBackoff = null;

      // When & Then: 应抛出 NullPointerException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      Duration.ofMinutes(5),
                      3,
                      Duration.ofSeconds(1),
                      2.0,
                      maxBackoff,
                      "worker-01"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("maxBackoff must not be null");
    }

    @Test
    @DisplayName("应该拒绝 leaseOwner 为 null")
    void shouldRejectNullLeaseOwner() {
      // Given: leaseOwner 为 null
      ChannelKey channel = createTestChannelKey("ingest", "task");
      String leaseOwner = null;

      // When & Then: 应抛出 NullPointerException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      Duration.ofMinutes(5),
                      3,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      leaseOwner))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("leaseOwner must not be null");
    }

    @Test
    @DisplayName("应该拒绝非正数的 batchSize（零）")
    void shouldRejectZeroBatchSize() {
      // Given: batchSize 为 0
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int batchSize = 0;

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      batchSize,
                      Duration.ofMinutes(5),
                      3,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchSize must be positive");
    }

    @Test
    @DisplayName("应该拒绝非正数的 batchSize（负数）")
    void shouldRejectNegativeBatchSize() {
      // Given: batchSize 为负数
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int batchSize = -10;

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      batchSize,
                      Duration.ofMinutes(5),
                      3,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("batchSize must be positive");
    }

    @Test
    @DisplayName("应该拒绝非正数的 maxAttempts（零）")
    void shouldRejectZeroMaxAttempts() {
      // Given: maxAttempts 为 0
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int maxAttempts = 0;

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      Duration.ofMinutes(5),
                      maxAttempts,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxAttempts must be positive");
    }

    @Test
    @DisplayName("应该拒绝非正数的 maxAttempts（负数）")
    void shouldRejectNegativeMaxAttempts() {
      // Given: maxAttempts 为负数
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int maxAttempts = -5;

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      Duration.ofMinutes(5),
                      maxAttempts,
                      Duration.ofSeconds(1),
                      2.0,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("maxAttempts must be positive");
    }

    @Test
    @DisplayName("应该拒绝小于 1.0 的 backoffMultiplier")
    void shouldRejectBackoffMultiplierLessThanOne() {
      // Given: backoffMultiplier 小于 1.0
      ChannelKey channel = createTestChannelKey("ingest", "task");
      double backoffMultiplier = 0.5;

      // When & Then: 应抛出 IllegalArgumentException
      assertThatThrownBy(
              () ->
                  new RelayPlan(
                      channel,
                      Instant.parse("2025-01-01T10:00:00Z"),
                      100,
                      Duration.ofMinutes(5),
                      3,
                      Duration.ofSeconds(1),
                      backoffMultiplier,
                      Duration.ofMinutes(10),
                      "worker-01"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("backoffMultiplier must be at least 1");
    }

    @Test
    @DisplayName("应该接受 backoffMultiplier 等于 1.0")
    void shouldAcceptBackoffMultiplierEqualsOne() {
      // Given: backoffMultiplier 等于 1.0
      ChannelKey channel = createTestChannelKey("ingest", "task");
      double backoffMultiplier = 1.0;

      // When: 创建 RelayPlan
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              backoffMultiplier,
              Duration.ofMinutes(10),
              "worker-01");

      // Then: 应该成功创建
      assertThat(plan.backoffMultiplier()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("应该接受 batchSize 为 1（最小有效值）")
    void shouldAcceptMinimalBatchSize() {
      // Given: batchSize 为 1
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int batchSize = 1;

      // When: 创建 RelayPlan
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              batchSize,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // Then: 应该成功创建
      assertThat(plan.batchSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该接受 maxAttempts 为 1（最小有效值）")
    void shouldAcceptMinimalMaxAttempts() {
      // Given: maxAttempts 为 1
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int maxAttempts = 1;

      // When: 创建 RelayPlan
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              maxAttempts,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // Then: 应该成功创建
      assertThat(plan.maxAttempts()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("字段访问器测试")
  class AccessorTests {

    @Test
    @DisplayName("channel() 应该返回正确的通道键")
    void channelAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 channel 的 RelayPlan
      ChannelKey channel = createTestChannelKey("registry", "schema");
      RelayPlan plan = createValidRelayPlan(channel);

      // When: 访问 channel
      ChannelKey result = plan.channel();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(channel);
      assertThat(result.domain()).isEqualTo("registry");
      assertThat(result.resource()).isEqualTo("schema");
      assertThat(result.channel()).isEqualTo("REGISTRY_SCHEMA");
    }

    @Test
    @DisplayName("channel() 应该能够返回 null")
    void channelAccessorShouldReturnNull() {
      // Given: 创建 channel 为 null 的 RelayPlan
      RelayPlan plan = createValidRelayPlan(null);

      // When: 访问 channel
      ChannelKey result = plan.channel();

      // Then: 应返回 null
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("triggeredAt() 应该返回正确的触发时间")
    void triggeredAtAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 triggeredAt 的 RelayPlan
      Instant triggeredAt = Instant.parse("2025-06-15T14:30:00Z");
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 访问 triggeredAt
      Instant result = plan.triggeredAt();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(triggeredAt);
    }

    @Test
    @DisplayName("batchSize() 应该返回正确的批次大小")
    void batchSizeAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 batchSize 的 RelayPlan
      int batchSize = 500;
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              batchSize,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 访问 batchSize
      int result = plan.batchSize();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(500);
    }

    @Test
    @DisplayName("leaseDuration() 应该返回正确的租期时长")
    void leaseDurationAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 leaseDuration 的 RelayPlan
      Duration leaseDuration = Duration.ofMinutes(15);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 访问 leaseDuration
      Duration result = plan.leaseDuration();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("maxAttempts() 应该返回正确的最大尝试次数")
    void maxAttemptsAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 maxAttempts 的 RelayPlan
      int maxAttempts = 10;
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              maxAttempts,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 访问 maxAttempts
      int result = plan.maxAttempts();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(10);
    }

    @Test
    @DisplayName("initialBackoff() 应该返回正确的初始退避时间")
    void initialBackoffAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 initialBackoff 的 RelayPlan
      Duration initialBackoff = Duration.ofSeconds(5);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              initialBackoff,
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 访问 initialBackoff
      Duration result = plan.initialBackoff();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("backoffMultiplier() 应该返回正确的退避倍数")
    void backoffMultiplierAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 backoffMultiplier 的 RelayPlan
      double backoffMultiplier = 3.5;
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              backoffMultiplier,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 访问 backoffMultiplier
      double result = plan.backoffMultiplier();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(3.5);
    }

    @Test
    @DisplayName("maxBackoff() 应该返回正确的最大退避时间")
    void maxBackoffAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 maxBackoff 的 RelayPlan
      Duration maxBackoff = Duration.ofHours(1);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              maxBackoff,
              "worker-01");

      // When: 访问 maxBackoff
      Duration result = plan.maxBackoff();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("leaseOwner() 应该返回正确的租期持有者")
    void leaseOwnerAccessorShouldReturnCorrectValue() {
      // Given: 创建带有特定 leaseOwner 的 RelayPlan
      String leaseOwner = "worker-production-42";
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              leaseOwner);

      // When: 访问 leaseOwner
      String result = plan.leaseOwner();

      // Then: 应返回正确的值
      assertThat(result).isEqualTo("worker-production-42");
    }
  }

  @Nested
  @DisplayName("业务方法测试")
  class BusinessMethodTests {

    @Test
    @DisplayName("leaseExpireAt() 应该返回正确的租期过期时间")
    void leaseExpireAtShouldCalculateCorrectExpirationTime() {
      // Given: 创建 RelayPlan，触发时间为 10:00，租期为 5 分钟
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Duration leaseDuration = Duration.ofMinutes(5);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 计算租期过期时间
      Instant expireAt = plan.leaseExpireAt();

      // Then: 应该是触发时间加上租期时长（10:05）
      assertThat(expireAt).isEqualTo(Instant.parse("2025-01-01T10:05:00Z"));
    }

    @Test
    @DisplayName("leaseExpireAt() 应该正确处理秒级租期")
    void leaseExpireAtShouldHandleSecondsLeaseDuration() {
      // Given: 创建 RelayPlan，触发时间为 10:00:00，租期为 30 秒
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Duration leaseDuration = Duration.ofSeconds(30);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 计算租期过期时间
      Instant expireAt = plan.leaseExpireAt();

      // Then: 应该是触发时间加上 30 秒（10:00:30）
      assertThat(expireAt).isEqualTo(Instant.parse("2025-01-01T10:00:30Z"));
    }

    @Test
    @DisplayName("leaseExpireAt() 应该正确处理小时级租期")
    void leaseExpireAtShouldHandleHoursLeaseDuration() {
      // Given: 创建 RelayPlan，触发时间为 10:00，租期为 2 小时
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Duration leaseDuration = Duration.ofHours(2);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 计算租期过期时间
      Instant expireAt = plan.leaseExpireAt();

      // Then: 应该是触发时间加上 2 小时（12:00）
      assertThat(expireAt).isEqualTo(Instant.parse("2025-01-01T12:00:00Z"));
    }

    @Test
    @DisplayName("leaseExpireAt() 应该正确处理跨天的租期")
    void leaseExpireAtShouldHandleCrossDayLeaseDuration() {
      // Given: 创建 RelayPlan，触发时间为 23:30，租期为 1 小时
      Instant triggeredAt = Instant.parse("2025-01-01T23:30:00Z");
      Duration leaseDuration = Duration.ofHours(1);
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 计算租期过期时间
      Instant expireAt = plan.leaseExpireAt();

      // Then: 应该跨越到第二天 00:30
      assertThat(expireAt).isEqualTo(Instant.parse("2025-01-02T00:30:00Z"));
    }

    @Test
    @DisplayName("leaseExpireAt() 应该正确处理零时长的租期")
    void leaseExpireAtShouldHandleZeroDuration() {
      // Given: 创建 RelayPlan，租期为零（虽然业务上不太可能）
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      Duration leaseDuration = Duration.ZERO;
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 计算租期过期时间
      Instant expireAt = plan.leaseExpireAt();

      // Then: 过期时间应该等于触发时间
      assertThat(expireAt).isEqualTo(triggeredAt);
    }
  }

  @Nested
  @DisplayName("Record 语义测试")
  class RecordSemanticsTests {

    @Test
    @DisplayName("equals() 应该在所有字段相同时返回 true")
    void equalsShouldReturnTrueWhenAllFieldsAreEqual() {
      // Given: 创建两个字段完全相同的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 两者应该相等
      assertThat(plan1).isEqualTo(plan2);
      assertThat(plan2).isEqualTo(plan1);
    }

    @Test
    @DisplayName("equals() 应该在 channel 不同时返回 false")
    void equalsShouldReturnFalseWhenChannelDiffers() {
      // Given: 创建两个 channel 不同的 RelayPlan
      ChannelKey channel1 = createTestChannelKey("ingest", "task");
      ChannelKey channel2 = createTestChannelKey("registry", "schema");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              channel1,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel2,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 两者应该不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该在 channel 为 null 与非 null 时返回 false")
    void equalsShouldReturnFalseWhenChannelIsNullVsNonNull() {
      // Given: 创建一个 channel 为 null，另一个不为 null 的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 两者应该不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该在 channel 都为 null 且其他字段相同时返回 true")
    void equalsShouldReturnTrueWhenBothChannelsAreNull() {
      // Given: 创建两个 channel 都为 null 的 RelayPlan
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              null,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 两者应该相等
      assertThat(plan1).isEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该在 triggeredAt 不同时返回 false")
    void equalsShouldReturnFalseWhenTriggeredAtDiffers() {
      // Given: 创建两个 triggeredAt 不同的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt1 = Instant.parse("2025-01-01T10:00:00Z");
      Instant triggeredAt2 = Instant.parse("2025-01-01T11:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              channel,
              triggeredAt1,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              triggeredAt2,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 两者应该不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该在 batchSize 不同时返回 false")
    void equalsShouldReturnFalseWhenBatchSizeDiffers() {
      // Given: 创建两个 batchSize 不同的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              triggeredAt,
              200,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 两者应该不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该在 leaseOwner 不同时返回 false")
    void equalsShouldReturnFalseWhenLeaseOwnerDiffers() {
      // Given: 创建两个 leaseOwner 不同的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-02");

      // When & Then: 两者应该不相等
      assertThat(plan1).isNotEqualTo(plan2);
    }

    @Test
    @DisplayName("equals() 应该在比较自身时返回 true")
    void equalsShouldReturnTrueWhenComparingSameInstance() {
      // Given: 创建一个 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan = createValidRelayPlan(channel);

      // When & Then: 与自身比较应该相等
      assertThat(plan).isEqualTo(plan);
    }

    @Test
    @DisplayName("equals() 应该在比较 null 时返回 false")
    void equalsShouldReturnFalseWhenComparingWithNull() {
      // Given: 创建一个 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan = createValidRelayPlan(channel);

      // When & Then: 与 null 比较应该不相等
      assertThat(plan).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals() 应该在比较不同类型对象时返回 false")
    void equalsShouldReturnFalseWhenComparingWithDifferentType() {
      // Given: 创建一个 RelayPlan 和一个字符串
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan = createValidRelayPlan(channel);
      String notAPlan = "not a RelayPlan";

      // When & Then: 与不同类型比较应该不相等
      assertThat(plan).isNotEqualTo(notAPlan);
    }

    @Test
    @DisplayName("hashCode() 应该在相等的对象中返回相同的值")
    void hashCodeShouldBeEqualForEqualObjects() {
      // Given: 创建两个字段完全相同的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan1 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: hashCode 应该相等
      assertThat(plan1.hashCode()).isEqualTo(plan2.hashCode());
    }

    @Test
    @DisplayName("hashCode() 应该在不同对象中返回不同的值（通常情况）")
    void hashCodeShouldBeDifferentForDifferentObjects() {
      // Given: 创建两个字段不同的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      RelayPlan plan1 =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");
      RelayPlan plan2 =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T11:00:00Z"),
              200,
              Duration.ofMinutes(10),
              5,
              Duration.ofSeconds(2),
              3.0,
              Duration.ofMinutes(20),
              "worker-02");

      // When & Then: hashCode 应该不同（虽然不保证，但通常如此）
      assertThat(plan1.hashCode()).isNotEqualTo(plan2.hashCode());
    }

    @Test
    @DisplayName("toString() 应该包含所有字段信息")
    void toStringShouldContainAllFields() {
      // Given: 创建一个 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.parse("2025-01-01T10:00:00Z");
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 调用 toString()
      String result = plan.toString();

      // Then: 应该包含所有字段的信息
      assertThat(result)
          .contains("RelayPlan")
          .contains("channel=")
          .contains("triggeredAt=2025-01-01T10:00:00Z")
          .contains("batchSize=100")
          .contains("leaseDuration=PT5M")
          .contains("maxAttempts=3")
          .contains("initialBackoff=PT1S")
          .contains("backoffMultiplier=2.0")
          .contains("maxBackoff=PT10M")
          .contains("leaseOwner=worker-01");
    }

    @Test
    @DisplayName("toString() 应该正确处理 null channel")
    void toStringShouldHandleNullChannel() {
      // Given: 创建一个 channel 为 null 的 RelayPlan
      RelayPlan plan =
          new RelayPlan(
              null,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When: 调用 toString()
      String result = plan.toString();

      // Then: 应该包含 "channel=null"
      assertThat(result).contains("RelayPlan").contains("channel=null");
    }
  }

  @Nested
  @DisplayName("边界值测试")
  class BoundaryValueTests {

    @Test
    @DisplayName("应该接受极大的 batchSize")
    void shouldAcceptVeryLargeBatchSize() {
      // Given: 创建一个 batchSize 为 Integer.MAX_VALUE 的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int batchSize = Integer.MAX_VALUE;
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              batchSize,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 应该成功创建
      assertThat(plan.batchSize()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受极大的 maxAttempts")
    void shouldAcceptVeryLargeMaxAttempts() {
      // Given: 创建一个 maxAttempts 为 Integer.MAX_VALUE 的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      int maxAttempts = Integer.MAX_VALUE;
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              maxAttempts,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 应该成功创建
      assertThat(plan.maxAttempts()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受极大的 backoffMultiplier")
    void shouldAcceptVeryLargeBackoffMultiplier() {
      // Given: 创建一个 backoffMultiplier 为 Double.MAX_VALUE 的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      double backoffMultiplier = Double.MAX_VALUE;
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              backoffMultiplier,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 应该成功创建
      assertThat(plan.backoffMultiplier()).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    @DisplayName("应该接受空字符串作为 leaseOwner")
    void shouldAcceptEmptyStringAsLeaseOwner() {
      // Given: 创建一个 leaseOwner 为空字符串的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      String leaseOwner = "";
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              leaseOwner);

      // When & Then: 应该成功创建
      assertThat(plan.leaseOwner()).isEmpty();
    }

    @Test
    @DisplayName("应该接受极小的 Instant（EPOCH）")
    void shouldAcceptEpochInstant() {
      // Given: 创建一个 triggeredAt 为 EPOCH 的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Instant triggeredAt = Instant.EPOCH;
      RelayPlan plan =
          new RelayPlan(
              channel,
              triggeredAt,
              100,
              Duration.ofMinutes(5),
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 应该成功创建
      assertThat(plan.triggeredAt()).isEqualTo(Instant.EPOCH);
    }

    @Test
    @DisplayName("应该接受极大的 Duration")
    void shouldAcceptVeryLargeDuration() {
      // Given: 创建一个 leaseDuration 为 365 天的 RelayPlan
      ChannelKey channel = createTestChannelKey("ingest", "task");
      Duration leaseDuration = Duration.ofDays(365);
      RelayPlan plan =
          new RelayPlan(
              channel,
              Instant.parse("2025-01-01T10:00:00Z"),
              100,
              leaseDuration,
              3,
              Duration.ofSeconds(1),
              2.0,
              Duration.ofMinutes(10),
              "worker-01");

      // When & Then: 应该成功创建
      assertThat(plan.leaseDuration()).isEqualTo(Duration.ofDays(365));
    }
  }

  // 辅助方法：创建测试用的 ChannelKey
  private static ChannelKey createTestChannelKey(String domain, String resource) {
    return new ChannelKey() {
      @Override
      public String domain() {
        return domain;
      }

      @Override
      public String resource() {
        return resource;
      }
    };
  }

  // 辅助方法：创建有效的 RelayPlan
  private static RelayPlan createValidRelayPlan(ChannelKey channel) {
    return new RelayPlan(
        channel,
        Instant.parse("2025-01-01T10:00:00Z"),
        100,
        Duration.ofMinutes(5),
        3,
        Duration.ofSeconds(1),
        2.0,
        Duration.ofMinutes(10),
        "worker-01");
  }
}
