package dev.linqibin.starter.batch.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// BatchProgressMetricNames 常量测试。
///
/// 验证指标名称常量符合命名规范：
///
/// - 使用 `.` 分隔符（Prometheus 会转换为 `_`）
/// - 遵循 `{domain}.{component}.{metric}` 层级结构
/// - 不包含 `patra_` 前缀（由 OTel Collector 统一添加）
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchProgressMetricNames 常量测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchProgressMetricNamesTest {

  @Test
  @DisplayName("ITEMS_READ 应该遵循命名规范")
  void itemsRead_shouldFollowNamingConvention() {
    assertThat(BatchProgressMetricNames.ITEMS_READ)
        .isEqualTo("batch.step.items.read")
        .doesNotStartWith("patra")
        .contains(".");
  }

  @Test
  @DisplayName("ITEMS_WRITTEN 应该遵循命名规范")
  void itemsWritten_shouldFollowNamingConvention() {
    assertThat(BatchProgressMetricNames.ITEMS_WRITTEN)
        .isEqualTo("batch.step.items.written")
        .doesNotStartWith("patra")
        .contains(".");
  }

  @Test
  @DisplayName("ITEMS_SKIPPED 应该遵循命名规范")
  void itemsSkipped_shouldFollowNamingConvention() {
    assertThat(BatchProgressMetricNames.ITEMS_SKIPPED)
        .isEqualTo("batch.step.items.skipped")
        .doesNotStartWith("patra")
        .contains(".");
  }

  @Test
  @DisplayName("COMMITS 应该遵循命名规范")
  void commits_shouldFollowNamingConvention() {
    assertThat(BatchProgressMetricNames.COMMITS)
        .isEqualTo("batch.step.commits")
        .doesNotStartWith("patra")
        .contains(".");
  }

  @Test
  @DisplayName("ROLLBACKS 应该遵循命名规范")
  void rollbacks_shouldFollowNamingConvention() {
    assertThat(BatchProgressMetricNames.ROLLBACKS)
        .isEqualTo("batch.step.rollbacks")
        .doesNotStartWith("patra")
        .contains(".");
  }

  @Test
  @DisplayName("TAG_JOB_NAME 应与 Spring Batch 内置指标标签一致")
  void tagJobName_shouldMatchSpringBatchBuiltInTag() {
    assertThat(BatchProgressMetricNames.TAG_JOB_NAME)
        .isEqualTo("spring.batch.job.name")
        .describedAs("需与 Spring Batch 内置指标的 spring.batch.job.name 标签一致，以便 Grafana 变量联动");
  }

  @Test
  @DisplayName("TAG_STEP_NAME 应与 Spring Batch 内置指标标签一致")
  void tagStepName_shouldMatchSpringBatchBuiltInTag() {
    assertThat(BatchProgressMetricNames.TAG_STEP_NAME)
        .isEqualTo("spring.batch.step.name")
        .describedAs("需与 Spring Batch 内置指标的 spring.batch.step.name 标签一致，以便 Grafana 变量联动");
  }
}
