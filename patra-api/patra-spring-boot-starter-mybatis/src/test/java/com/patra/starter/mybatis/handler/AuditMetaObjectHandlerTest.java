package com.patra.starter.mybatis.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AuditMetaObjectHandler} 的单元测试。
 *
 * <p>测试策略：验证 AuditMetaObjectHandler 的时钟配置和时间获取逻辑。
 *
 * <p>关键测试点：
 *
 * <ul>
 *   <li>使用固定时钟进行可控测试
 *   <li>使用系统时钟的默认行为
 *   <li>验证构造函数接受 null Clock 参数
 * </ul>
 *
 * <p>注意：由于 MyBatis-Plus 的 strictInsertFill/strictUpdateFill 依赖运行时 TableInfo 元数据，
 * 本测试重点验证时钟配置逻辑。完整的填充行为需要在集成测试中验证。
 */
@DisplayName("AuditMetaObjectHandler 单元测试")
class AuditMetaObjectHandlerTest {

  private static final Instant FIXED_TIME = Instant.parse("2025-01-15T10:00:00Z");

  @Test
  @DisplayName("使用固定时钟应返回固定时间")
  void constructor_withFixedClock_shouldUseThatClock() {
    // Arrange
    Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneId.of("UTC"));

    // Act
    AuditMetaObjectHandler handler = new AuditMetaObjectHandler(fixedClock);

    // Assert
    // 通过反射验证 clock 字段被正确设置
    assertThat(handler).isNotNull();
    assertThat(handler).hasFieldOrPropertyWithValue("clock", fixedClock);
  }

  @Test
  @DisplayName("使用 null Clock 应能正常构造")
  void constructor_withNullClock_shouldNotThrowException() {
    // Act & Assert
    AuditMetaObjectHandler handler = new AuditMetaObjectHandler(null);

    assertThat(handler).isNotNull();
    assertThat(handler).hasFieldOrPropertyWithValue("clock", null);
  }

  @Test
  @DisplayName("使用不同时区的时钟应能正常构造")
  void constructor_withDifferentTimezone_shouldWork() {
    // Arrange
    Instant customTime = Instant.parse("2024-06-15T08:45:00Z");
    Clock tokyoClock = Clock.fixed(customTime, ZoneId.of("Asia/Tokyo"));

    // Act
    AuditMetaObjectHandler handler = new AuditMetaObjectHandler(tokyoClock);

    // Assert
    assertThat(handler).isNotNull();
    assertThat(handler).hasFieldOrPropertyWithValue("clock", tokyoClock);
  }

  @Test
  @DisplayName("使用系统时钟应能正常构造")
  void constructor_withSystemClock_shouldWork() {
    // Arrange
    Clock systemClock = Clock.systemUTC();

    // Act
    AuditMetaObjectHandler handler = new AuditMetaObjectHandler(systemClock);

    // Assert
    assertThat(handler).isNotNull();
    assertThat(handler).hasFieldOrPropertyWithValue("clock", systemClock);
  }

  @Test
  @DisplayName("验证 MetaObjectHandler 接口实现")
  void shouldImplementMetaObjectHandler() {
    // Arrange
    AuditMetaObjectHandler handler = new AuditMetaObjectHandler(null);

    // Assert - 验证类实现了正确的接口
    assertThat(handler)
        .isInstanceOf(com.baomidou.mybatisplus.core.handlers.MetaObjectHandler.class);
  }
}
