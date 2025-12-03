package com.patra.catalog.app.usecase.venue.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.patra.catalog.domain.exception.CatalogScheduleParameterException;
import com.patra.catalog.domain.model.enums.DataImportMode;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/// Venue 导入命令单元测试。
///
/// **测试策略**：
///
/// - 验证工厂方法正确创建命令
/// - 验证模式字符串转换
/// - 验证参数约束
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueImportCommand 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueImportCommandTest {

  @Nested
  @DisplayName("工厂方法测试")
  class FactoryMethodTest {

    @Test
    @DisplayName("incremental() - 应该创建增量导入命令")
    void incremental_shouldCreateIncrementalMode() {
      // When
      VenueImportCommand command = VenueImportCommand.incremental();

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.INCREMENTAL);
    }

    @Test
    @DisplayName("truncateReimport() - 应该创建清空重导入命令")
    void truncateReimport_shouldCreateTruncateMode() {
      // When
      VenueImportCommand command = VenueImportCommand.truncateReimport();

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.TRUNCATE_REIMPORT);
    }
  }

  @Nested
  @DisplayName("of() 方法测试")
  class OfMethodTest {

    @Test
    @DisplayName("大写 INCREMENTAL - 应该正确创建命令")
    void uppercaseIncremental_shouldCreateCommand() {
      // When
      VenueImportCommand command = VenueImportCommand.of("INCREMENTAL");

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.INCREMENTAL);
    }

    @Test
    @DisplayName("大写 TRUNCATE_REIMPORT - 应该正确创建命令")
    void uppercaseTruncate_shouldCreateCommand() {
      // When
      VenueImportCommand command = VenueImportCommand.of("TRUNCATE_REIMPORT");

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.TRUNCATE_REIMPORT);
    }

    @Test
    @DisplayName("小写 mode 字符串 - 应该正确转换")
    void lowercaseModeString_shouldConvert() {
      // When
      VenueImportCommand command = VenueImportCommand.of("incremental");

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.INCREMENTAL);
    }

    @Test
    @DisplayName("混合大小写 mode 字符串 - 应该正确转换")
    void mixedCaseModeString_shouldConvert() {
      // When
      VenueImportCommand command = VenueImportCommand.of("Truncate_Reimport");

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.TRUNCATE_REIMPORT);
    }

    @Test
    @DisplayName("带空格的 mode 字符串 - 应该正确处理")
    void modeStringWithSpaces_shouldTrim() {
      // When
      VenueImportCommand command = VenueImportCommand.of("  INCREMENTAL  ");

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.INCREMENTAL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    @DisplayName("空白 mode 字符串 - 应该抛出 CatalogScheduleParameterException")
    void blankModeString_shouldThrowException(String modeStr) {
      // When & Then
      assertThatThrownBy(() -> VenueImportCommand.of(modeStr))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("mode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_MODE", "FULL", "DELTA", "APPEND"})
    @DisplayName("无效 mode 字符串 - 应该抛出 CatalogScheduleParameterException")
    void invalidModeString_shouldThrowException(String modeStr) {
      // When & Then
      assertThatThrownBy(() -> VenueImportCommand.of(modeStr))
          .isInstanceOf(CatalogScheduleParameterException.class)
          .hasMessageContaining("非法的导入模式值");
    }
  }

  @Nested
  @DisplayName("构造函数验证测试")
  class ConstructorValidationTest {

    @Test
    @DisplayName("null mode - 应该抛出 NullPointerException")
    void nullMode_shouldThrowException() {
      // When & Then
      assertThatThrownBy(() -> new VenueImportCommand(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("mode");
    }

    @Test
    @DisplayName("有效 mode - 应该创建成功")
    void validMode_shouldCreateSuccessfully() {
      // When
      VenueImportCommand command = new VenueImportCommand(DataImportMode.INCREMENTAL);

      // Then
      assertThat(command.mode()).isEqualTo(DataImportMode.INCREMENTAL);
    }
  }
}
