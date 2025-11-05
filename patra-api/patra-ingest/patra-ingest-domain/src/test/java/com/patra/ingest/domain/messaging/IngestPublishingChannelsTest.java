package com.patra.ingest.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.ingest.domain.model.vo.execution.TaskReadyMessage;
import com.patra.ingest.domain.model.vo.relay.LiteratureReadyMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IngestPublishingChannels 枚举测试")
class IngestPublishingChannelsTest {

  @Nested
  @DisplayName("枚举常量存在性验证")
  class EnumConstantExistence {

    @Test
    @DisplayName("应该包含 TASK_READY 枚举值")
    void shouldContainTaskReadyConstant() {
      // When & Then
      assertThat(IngestPublishingChannels.TASK_READY).isNotNull();
    }

    @Test
    @DisplayName("应该包含 LITERATURE_DATA_READY 枚举值")
    void shouldContainLiteratureDataReadyConstant() {
      // When & Then
      assertThat(IngestPublishingChannels.LITERATURE_DATA_READY).isNotNull();
    }

    @Test
    @DisplayName("应该包含且仅包含 2 个枚举值")
    void shouldHaveExactlyTwoValues() {
      // When
      IngestPublishingChannels[] values = IngestPublishingChannels.values();

      // Then
      assertThat(values).hasSize(2);
    }
  }

  @Nested
  @DisplayName("TASK_READY 枚举值测试")
  class TaskReadyConstant {

    @Test
    @DisplayName("domain() 应该返回 'INGEST'")
    void domainShouldReturnIngest() {
      // When
      String domain = IngestPublishingChannels.TASK_READY.domain();

      // Then
      assertThat(domain).isEqualTo("INGEST");
    }

    @Test
    @DisplayName("resource() 应该返回 'TASK'")
    void resourceShouldReturnTask() {
      // When
      String resource = IngestPublishingChannels.TASK_READY.resource();

      // Then
      assertThat(resource).isEqualTo("TASK");
    }

    @Test
    @DisplayName("event() 应该返回 'READY'")
    void eventShouldReturnReady() {
      // When
      String event = IngestPublishingChannels.TASK_READY.event();

      // Then
      assertThat(event).isEqualTo("READY");
    }

    @Test
    @DisplayName("channel() 应该返回 'INGEST_TASK_READY'")
    void channelShouldReturnFormattedName() {
      // When
      String channel = IngestPublishingChannels.TASK_READY.channel();

      // Then
      assertThat(channel).isEqualTo("INGEST_TASK_READY");
    }

    @Test
    @DisplayName("payloadType() 应该返回 TaskReadyMessage.class")
    void payloadTypeShouldReturnTaskReadyMessage() {
      // When
      Class<?> payloadType = IngestPublishingChannels.TASK_READY.payloadType();

      // Then
      assertThat(payloadType).isEqualTo(TaskReadyMessage.class);
    }
  }

  @Nested
  @DisplayName("LITERATURE_DATA_READY 枚举值测试")
  class LiteratureDataReadyConstant {

    @Test
    @DisplayName("domain() 应该返回 'INGEST'")
    void domainShouldReturnIngest() {
      // When
      String domain = IngestPublishingChannels.LITERATURE_DATA_READY.domain();

      // Then
      assertThat(domain).isEqualTo("INGEST");
    }

    @Test
    @DisplayName("resource() 应该返回 'LITERATURE'")
    void resourceShouldReturnLiterature() {
      // When
      String resource = IngestPublishingChannels.LITERATURE_DATA_READY.resource();

      // Then
      assertThat(resource).isEqualTo("LITERATURE");
    }

    @Test
    @DisplayName("event() 应该返回 'DATA_READY'")
    void eventShouldReturnDataReady() {
      // When
      String event = IngestPublishingChannels.LITERATURE_DATA_READY.event();

      // Then
      assertThat(event).isEqualTo("DATA_READY");
    }

    @Test
    @DisplayName("channel() 应该返回 'INGEST_LITERATURE_DATA_READY'")
    void channelShouldReturnFormattedName() {
      // When
      String channel = IngestPublishingChannels.LITERATURE_DATA_READY.channel();

      // Then
      assertThat(channel).isEqualTo("INGEST_LITERATURE_DATA_READY");
    }

    @Test
    @DisplayName("payloadType() 应该返回 LiteratureReadyMessage.class")
    void payloadTypeShouldReturnLiteratureReadyMessage() {
      // When
      Class<?> payloadType = IngestPublishingChannels.LITERATURE_DATA_READY.payloadType();

      // Then
      assertThat(payloadType).isEqualTo(LiteratureReadyMessage.class);
    }
  }

  @Nested
  @DisplayName("fromChannel 静态方法 - 成功匹配")
  class FromChannelMethodSuccessCase {

    @Test
    @DisplayName("应该解析 'INGEST_TASK_READY' 为 TASK_READY")
    void shouldParseIngestTaskReady() {
      // Given
      String channel = "INGEST_TASK_READY";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.TASK_READY);
    }

    @Test
    @DisplayName("应该解析 'INGEST_LITERATURE_DATA_READY' 为 LITERATURE_DATA_READY")
    void shouldParseIngestLiteratureDataReady() {
      // Given
      String channel = "INGEST_LITERATURE_DATA_READY";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.LITERATURE_DATA_READY);
    }

    @Test
    @DisplayName("应该解析小写的通道名称")
    void shouldParseLowercaseChannelName() {
      // Given
      String channel = "ingest_task_ready";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.TASK_READY);
    }

    @Test
    @DisplayName("应该解析混合大小写的通道名称")
    void shouldParseMixedCaseChannelName() {
      // Given
      String channel = "InGeSt_TaSk_ReAdY";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.TASK_READY);
    }

    @Test
    @DisplayName("应该修剪前导和尾随空白")
    void shouldTrimWhitespace() {
      // Given
      String channel = "  INGEST_TASK_READY  ";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.TASK_READY);
    }
  }

  @Nested
  @DisplayName("fromChannel 静态方法 - 失败场景")
  class FromChannelMethodFailureCase {

    @Test
    @DisplayName("当通道名称为 null 时应该返回 empty")
    void shouldReturnEmptyForNull() {
      // Given
      String channel = null;

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当通道名称为空字符串时应该返回 empty")
    void shouldReturnEmptyForEmptyString() {
      // Given
      String channel = "";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当通道名称为空白字符串时应该返回 empty")
    void shouldReturnEmptyForBlankString() {
      // Given
      String channel = "   ";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当通道名称不匹配时应该返回 empty")
    void shouldReturnEmptyForUnknownChannel() {
      // Given
      String channel = "UNKNOWN_CHANNEL";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("当通道名称部分匹配时应该返回 empty")
    void shouldReturnEmptyForPartialMatch() {
      // Given
      String channel = "INGEST_TASK";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("枚举基本行为")
  class EnumBasicBehavior {

    @Test
    @DisplayName("valueOf 应该正确解析枚举名称")
    void valueOfShouldParseEnumName() {
      // When
      IngestPublishingChannels result = IngestPublishingChannels.valueOf("TASK_READY");

      // Then
      assertThat(result).isEqualTo(IngestPublishingChannels.TASK_READY);
    }

    @Test
    @DisplayName("name() 应该返回枚举常量名称")
    void nameShouldReturnConstantName() {
      // When
      String name = IngestPublishingChannels.TASK_READY.name();

      // Then
      assertThat(name).isEqualTo("TASK_READY");
    }

    @Test
    @DisplayName("ordinal() 应该返回正确的索引")
    void ordinalShouldReturnCorrectIndex() {
      // Then
      assertThat(IngestPublishingChannels.TASK_READY.ordinal()).isZero();
      assertThat(IngestPublishingChannels.LITERATURE_DATA_READY.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("toString() 应该返回枚举常量名称")
    void toStringShouldReturnConstantName() {
      // When
      String str = IngestPublishingChannels.TASK_READY.toString();

      // Then
      assertThat(str).isEqualTo("TASK_READY");
    }
  }

  @Nested
  @DisplayName("命名规范一致性")
  class NamingConsistency {

    @Test
    @DisplayName("所有枚举值的 domain 应该相同")
    void allValuesShouldHaveSameDomain() {
      // When & Then
      for (IngestPublishingChannels channel : IngestPublishingChannels.values()) {
        assertThat(channel.domain()).isEqualTo("INGEST");
      }
    }

    @Test
    @DisplayName("所有枚举值的 channel() 应该使用大写蛇形命名")
    void allChannelsShouldUseUpperSnakeCase() {
      // When & Then
      for (IngestPublishingChannels channel : IngestPublishingChannels.values()) {
        assertThat(channel.channel()).matches("^[A-Z_]+$");
      }
    }

    @Test
    @DisplayName("所有枚举值的 channel() 应该包含 domain、resource 和 event")
    void allChannelsShouldContainAllParts() {
      // When & Then
      for (IngestPublishingChannels channel : IngestPublishingChannels.values()) {
        String channelName = channel.channel();
        assertThat(channelName)
            .contains(channel.domain())
            .contains(channel.resource())
            .contains(channel.event());
      }
    }

    @Test
    @DisplayName("所有枚举值都应该有关联的 payloadType")
    void allValuesShouldHavePayloadType() {
      // When & Then
      for (IngestPublishingChannels channel : IngestPublishingChannels.values()) {
        assertThat(channel.payloadType()).isNotNull();
      }
    }
  }

  @Nested
  @DisplayName("ChannelKey 接口实现")
  class ChannelKeyInterfaceImplementation {

    @Test
    @DisplayName("应该正确实现 ChannelKey 接口")
    void shouldImplementChannelKeyInterface() {
      // Given
      IngestPublishingChannels channel = IngestPublishingChannels.TASK_READY;

      // Then
      assertThat(channel)
          .isInstanceOf(com.patra.common.messaging.ChannelKey.class);
    }

    @Test
    @DisplayName("channel() 方法应该按 DOMAIN_RESOURCE_EVENT 格式生成通道名")
    void channelMethodShouldFollowNamingPattern() {
      // Given
      IngestPublishingChannels channel = IngestPublishingChannels.TASK_READY;

      // When
      String expected = channel.domain() + "_" + channel.resource() + "_" + channel.event();
      String actual = channel.channel();

      // Then
      assertThat(actual).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("与 channel() 的双向映射一致性")
  class BidirectionalMappingConsistency {

    @Test
    @DisplayName("TASK_READY.channel() 应该能被 fromChannel() 解析回来")
    void taskReadyChannelShouldBeParsableBack() {
      // Given
      String channel = IngestPublishingChannels.TASK_READY.channel();

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.TASK_READY);
    }

    @Test
    @DisplayName("LITERATURE_DATA_READY.channel() 应该能被 fromChannel() 解析回来")
    void literatureDataReadyChannelShouldBeParsableBack() {
      // Given
      String channel = IngestPublishingChannels.LITERATURE_DATA_READY.channel();

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result)
          .isPresent()
          .contains(IngestPublishingChannels.LITERATURE_DATA_READY);
    }

    @Test
    @DisplayName("所有枚举值都应该支持双向映射")
    void allValuesShouldSupportBidirectionalMapping() {
      // When & Then
      for (IngestPublishingChannels expected : IngestPublishingChannels.values()) {
        String channel = expected.channel();
        var actual = IngestPublishingChannels.fromChannel(channel);
        assertThat(actual)
            .isPresent()
            .contains(expected);
      }
    }
  }
}
