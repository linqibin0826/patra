package com.patra.catalog.app.usecase.venue.initialize.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Venue 初始化命令单元测试。
///
/// **测试策略**：
///
/// - 验证工厂方法正确创建命令
/// - 验证 record 不可变性
///
/// **设计说明**：
///
/// VenueInitializeCommand 是空 record（不接受任何参数），因为：
///
/// - Venue 从 OpenAlex S3 Manifest 动态获取分区文件列表（不需要 URL）
/// - OpenAlex 使用 updated_date 分区管理版本（不需要版本号）
/// - 导入语义固定为「一次性初始化」（不需要模式参数）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("VenueInitializeCommand 单元测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class VenueInitializeCommandTest {

  @Test
  @DisplayName("create() - 应该成功创建初始化命令")
  void create_shouldCreateCommand() {
    // When
    VenueInitializeCommand command = VenueInitializeCommand.create();

    // Then
    assertThat(command).isNotNull();
  }

  @Test
  @DisplayName("create() - 多次调用应该返回不同实例（record 行为验证）")
  void create_shouldReturnNewInstances() {
    // When
    VenueInitializeCommand command1 = VenueInitializeCommand.create();
    VenueInitializeCommand command2 = VenueInitializeCommand.create();

    // Then - 虽然是不同实例，但空 record 的 equals 应该相等
    assertThat(command1).isNotSameAs(command2);
    assertThat(command1).isEqualTo(command2);
  }

  @Test
  @DisplayName("构造函数 - 应该成功创建命令")
  void constructor_shouldCreateCommand() {
    // When
    VenueInitializeCommand command = new VenueInitializeCommand();

    // Then
    assertThat(command).isNotNull();
  }
}
