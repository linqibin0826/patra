package dev.patra.notification.domain.realtime.model.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/// 实例 ID：每个通知服务实例启动时生成一个，用来认出自己发的广播。
class InstanceIdTest {

  /// 同一个 JVM 里起两个实例（集成测试）也必须不同。
  @Test
  void should_generate_different_values_each_time() {
    assertThat(InstanceId.random()).isNotEqualTo(InstanceId.random());
  }

  /// 空白的标识认不出任何实例。
  @Test
  void should_reject_blank_value() {
    assertThatThrownBy(() -> new InstanceId(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new InstanceId(null)).isInstanceOf(IllegalArgumentException.class);
  }
}
