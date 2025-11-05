package com.patra.ingest.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsumerGroups 消费者组名称生成器测试")
class ConsumerGroupsTest {

  @Nested
  @DisplayName("svc 方法 - 标准场景")
  class SvcMethodStandardCases {

    @Test
    @DisplayName("应该生成标准的小写 kebab-case 消费者组名称")
    void shouldGenerateStandardKebabCaseName() {
      // Given
      String service = "ingest";
      String consumer = "task-ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }

    @Test
    @DisplayName("应该将大写服务名转换为小写")
    void shouldConvertUppercaseServiceToLowercase() {
      // Given
      String service = "REGISTRY";
      String consumer = "task-ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-registry-task-ready-cg");
    }

    @Test
    @DisplayName("应该将大写消费者标识转换为小写")
    void shouldConvertUppercaseConsumerToLowercase() {
      // Given
      String service = "ingest";
      String consumer = "Provenance";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-provenance-cg");
    }

    @Test
    @DisplayName("应该将混合大小写转换为小写")
    void shouldConvertMixedCaseToLowercase() {
      // Given
      String service = "InGeSt";
      String consumer = "TaSk-ReAdY";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }
  }

  @Nested
  @DisplayName("svc 方法 - 下划线转换")
  class SvcMethodUnderscoreConversion {

    @Test
    @DisplayName("应该将服务名中的下划线转换为短横线")
    void shouldConvertUnderscoreToHyphenInService() {
      // Given
      String service = "ingest_service";
      String consumer = "task-ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-service-task-ready-cg");
    }

    @Test
    @DisplayName("应该将消费者标识中的下划线转换为短横线")
    void shouldConvertUnderscoreToHyphenInConsumer() {
      // Given
      String service = "ingest";
      String consumer = "task_ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }

    @Test
    @DisplayName("应该将多个下划线都转换为短横线")
    void shouldConvertMultipleUnderscoresToHyphens() {
      // Given
      String service = "ingest_data_service";
      String consumer = "task_ready_handler";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-data-service-task-ready-handler-cg");
    }
  }

  @Nested
  @DisplayName("svc 方法 - 空白字符处理")
  class SvcMethodWhitespaceHandling {

    @Test
    @DisplayName("应该修剪服务名的前导空白")
    void shouldTrimLeadingWhitespaceInService() {
      // Given
      String service = "  ingest";
      String consumer = "task-ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }

    @Test
    @DisplayName("应该修剪服务名的尾随空白")
    void shouldTrimTrailingWhitespaceInService() {
      // Given
      String service = "ingest  ";
      String consumer = "task-ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }

    @Test
    @DisplayName("应该修剪消费者标识的前导和尾随空白")
    void shouldTrimWhitespaceInConsumer() {
      // Given
      String service = "ingest";
      String consumer = "  task-ready  ";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }

    @Test
    @DisplayName("应该修剪两个参数的前导和尾随空白")
    void shouldTrimWhitespaceInBothParameters() {
      // Given
      String service = "  ingest  ";
      String consumer = "  task-ready  ";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }
  }

  @Nested
  @DisplayName("svc 方法 - Null 处理")
  class SvcMethodNullHandling {

    @Test
    @DisplayName("当服务名为 null 时应该使用 'unknown'")
    void shouldUseUnknownForNullService() {
      // Given
      String service = null;
      String consumer = "task-ready";

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-unknown-task-ready-cg");
    }

    @Test
    @DisplayName("当消费者标识为 null 时应该使用 'unknown'")
    void shouldUseUnknownForNullConsumer() {
      // Given
      String service = "ingest";
      String consumer = null;

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-ingest-unknown-cg");
    }

    @Test
    @DisplayName("当两个参数都为 null 时应该使用 'unknown'")
    void shouldUseUnknownForBothNull() {
      // Given
      String service = null;
      String consumer = null;

      // When
      String result = ConsumerGroups.svc(service, consumer);

      // Then
      assertThat(result).isEqualTo("svc-unknown-unknown-cg");
    }
  }

  @Nested
  @DisplayName("svc 方法 - 命名格式验证")
  class SvcMethodNamingFormat {

    @Test
    @DisplayName("生成的名称应该以 'svc-' 开头")
    void shouldStartWithSvcPrefix() {
      // When
      String result = ConsumerGroups.svc("ingest", "task-ready");

      // Then
      assertThat(result).startsWith("svc-");
    }

    @Test
    @DisplayName("生成的名称应该以 '-cg' 结尾")
    void shouldEndWithCgSuffix() {
      // When
      String result = ConsumerGroups.svc("ingest", "task-ready");

      // Then
      assertThat(result).endsWith("-cg");
    }

    @Test
    @DisplayName("生成的名称应该包含服务名")
    void shouldContainServiceName() {
      // When
      String result = ConsumerGroups.svc("ingest", "task-ready");

      // Then
      assertThat(result).contains("ingest");
    }

    @Test
    @DisplayName("生成的名称应该包含消费者标识")
    void shouldContainConsumerName() {
      // When
      String result = ConsumerGroups.svc("ingest", "task-ready");

      // Then
      assertThat(result).contains("task-ready");
    }

    @Test
    @DisplayName("生成的名称应该使用短横线分隔各部分")
    void shouldUseDashAsSeparator() {
      // When
      String result = ConsumerGroups.svc("ingest", "task-ready");

      // Then
      assertThat(result)
          .matches("^svc-[a-z0-9-]+-[a-z0-9-]+-cg$");
    }
  }

  @Nested
  @DisplayName("svc 方法 - 文档示例验证")
  class SvcMethodDocumentationExamples {

    @Test
    @DisplayName("应该匹配文档中的第一个示例")
    void shouldMatchFirstDocumentationExample() {
      // Given & When
      String result = ConsumerGroups.svc("ingest", "task-ready");

      // Then
      assertThat(result).isEqualTo("svc-ingest-task-ready-cg");
    }

    @Test
    @DisplayName("应该匹配文档中的第二个示例（大写转换）")
    void shouldMatchSecondDocumentationExample() {
      // Given & When
      String result = ConsumerGroups.svc("REGISTRY", "Provenance");

      // Then
      assertThat(result).isEqualTo("svc-registry-provenance-cg");
    }
  }
}
