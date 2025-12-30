package com.patra.starter.jpa.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// {@link SoftDeletable} 接口单元测试。
///
/// 测试策略: 纯单元测试，验证 default 方法的行为。
///
/// 测试覆盖:
///
/// - ✅ `softDelete()` 方法 - 设置删除时间戳
/// - ✅ `isDeleted()` 方法 - 判断删除状态
/// - ✅ 边界条件 - null 值处理
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SoftDeletable 接口单元测试")
class SoftDeletableTest {

  private TestSoftDeletable entity;

  @BeforeEach
  void setUp() {
    entity = new TestSoftDeletable();
  }

  @Nested
  @DisplayName("softDelete() 方法测试")
  class SoftDeleteTests {

    @Test
    @DisplayName("应该设置 deletedAt 为当前时间戳")
    void shouldSetDeletedAtToCurrentTimestamp() {
      // Given
      Instant before = Instant.now();

      // When
      entity.softDelete();

      // Then
      Instant after = Instant.now();
      assertThat(entity.getDeletedAt())
          .isNotNull()
          .isAfterOrEqualTo(before)
          .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("调用 softDelete() 后 isDeleted() 应该返回 true")
    void shouldMarkEntityAsDeletedAfterSoftDelete() {
      // Given
      assertThat(entity.isDeleted()).isFalse();

      // When
      entity.softDelete();

      // Then
      assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("多次调用 softDelete() 应该更新时间戳")
    void shouldUpdateTimestampOnMultipleSoftDeleteCalls() throws InterruptedException {
      // Given
      entity.softDelete();
      Instant firstDeletedAt = entity.getDeletedAt();

      // When - 等待一小段时间后再次删除
      Thread.sleep(10);
      entity.softDelete();

      // Then
      assertThat(entity.getDeletedAt()).isNotNull().isAfter(firstDeletedAt);
    }
  }

  @Nested
  @DisplayName("isDeleted() 方法测试")
  class IsDeletedTests {

    @Test
    @DisplayName("deletedAt 为 null 时应该返回 false")
    void shouldReturnFalseWhenDeletedAtIsNull() {
      // Given
      entity.setDeletedAt(null);

      // When & Then
      assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("deletedAt 有值时应该返回 true")
    void shouldReturnTrueWhenDeletedAtHasValue() {
      // Given
      entity.setDeletedAt(Instant.now());

      // When & Then
      assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("deletedAt 为过去时间时应该返回 true")
    void shouldReturnTrueWhenDeletedAtIsPastTime() {
      // Given
      entity.setDeletedAt(Instant.now().minus(Duration.ofDays(30)));

      // When & Then
      assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("deletedAt 为未来时间时应该返回 true")
    void shouldReturnTrueWhenDeletedAtIsFutureTime() {
      // Given - 虽然不常见，但技术上是有效的
      entity.setDeletedAt(Instant.now().plus(Duration.ofDays(30)));

      // When & Then
      assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("deletedAt 为 Epoch 起点时应该返回 true")
    void shouldReturnTrueWhenDeletedAtIsEpoch() {
      // Given
      entity.setDeletedAt(Instant.EPOCH);

      // When & Then
      assertThat(entity.isDeleted()).isTrue();
    }
  }

  @Nested
  @DisplayName("状态转换测试")
  class StateTransitionTests {

    @Test
    @DisplayName("新实体应该处于未删除状态")
    void newEntityShouldBeNotDeleted() {
      // Given & When
      TestSoftDeletable newEntity = new TestSoftDeletable();

      // Then
      assertThat(newEntity.isDeleted()).isFalse();
      assertThat(newEntity.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("手动设置 deletedAt 为 null 应该恢复未删除状态")
    void shouldRestoreNotDeletedStateWhenSettingDeletedAtToNull() {
      // Given
      entity.softDelete();
      assertThat(entity.isDeleted()).isTrue();

      // When
      entity.setDeletedAt(null);

      // Then
      assertThat(entity.isDeleted()).isFalse();
    }
  }

  /// 测试用 SoftDeletable 实现类。
  private static class TestSoftDeletable implements SoftDeletable {

    private Instant deletedAt;

    @Override
    public Instant getDeletedAt() {
      return deletedAt;
    }

    @Override
    public void setDeletedAt(Instant deletedAt) {
      this.deletedAt = deletedAt;
    }
  }
}
