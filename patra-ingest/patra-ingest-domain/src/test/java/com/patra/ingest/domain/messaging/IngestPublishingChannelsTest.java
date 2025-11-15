package com.patra.ingest.domain.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.ingest.domain.model.vo.execution.TaskReadyMessage;
import com.patra.ingest.domain.model.vo.relay.LiteratureReadyMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IngestPublishingChannels 枚举测试（重构为两段式 Channel）")
class IngestPublishingChannelsTest {

  @Nested
  @DisplayName("枚举常量存在性验证")
  class EnumConstantExistence {

    @Test
    @DisplayName("应该包含 TASK 枚举值")
    void shouldContainTaskConstant() {
      // When & Then
      assertThat(IngestPublishingChannels.TASK).isNotNull();
    }

    @Test
    @DisplayName("应该包含 LITERATURE 枚举值")
    void shouldContainLiteratureConstant() {
      // When & Then
      assertThat(IngestPublishingChannels.LITERATURE).isNotNull();
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
  @DisplayName("TASK 枚举值测试")
  class TaskConstant {

    @Test
    @DisplayName("domain() 应该返回 'INGEST'")
    void domainShouldReturnIngest() {
      // When
      String domain = IngestPublishingChannels.TASK.domain();

      // Then
      assertThat(domain).isEqualTo("INGEST");
    }

    @Test
    @DisplayName("resource() 应该返回 'TASK'")
    void resourceShouldReturnTask() {
      // When
      String resource = IngestPublishingChannels.TASK.resource();

      // Then
      assertThat(resource).isEqualTo("TASK");
    }

    @Test
    @DisplayName("channel() 应该返回 'INGEST_TASK'")
    void channelShouldReturnFormattedName() {
      // When
      String channel = IngestPublishingChannels.TASK.channel();

      // Then
      assertThat(channel).isEqualTo("INGEST_TASK");
    }

    @Test
    @DisplayName("payloadType() 应该返回 TaskReadyMessage.class")
    void payloadTypeShouldReturnTaskReadyMessage() {
      // When
      Class<?> payloadType = IngestPublishingChannels.TASK.payloadType();

      // Then
      assertThat(payloadType).isEqualTo(TaskReadyMessage.class);
    }
  }

  @Nested
  @DisplayName("LITERATURE 枚举值测试")
  class LiteratureConstant {

    @Test
    @DisplayName("domain() 应该返回 'INGEST'")
    void domainShouldReturnIngest() {
      // When
      String domain = IngestPublishingChannels.LITERATURE.domain();

      // Then
      assertThat(domain).isEqualTo("INGEST");
    }

    @Test
    @DisplayName("resource() 应该返回 'LITERATURE'")
    void resourceShouldReturnLiterature() {
      // When
      String resource = IngestPublishingChannels.LITERATURE.resource();

      // Then
      assertThat(resource).isEqualTo("LITERATURE");
    }

    @Test
    @DisplayName("channel() 应该返回 'INGEST_LITERATURE'")
    void channelShouldReturnFormattedName() {
      // When
      String channel = IngestPublishingChannels.LITERATURE.channel();

      // Then
      assertThat(channel).isEqualTo("INGEST_LITERATURE");
    }

    @Test
    @DisplayName("payloadType() 应该返回 LiteratureReadyMessage.class")
    void payloadTypeShouldReturnLiteratureReadyMessage() {
      // When
      Class<?> payloadType = IngestPublishingChannels.LITERATURE.payloadType();

      // Then
      assertThat(payloadType).isEqualTo(LiteratureReadyMessage.class);
    }
  }

  @Nested
  @DisplayName("fromChannel 静态方法 - 成功匹配")
  class FromChannelMethodSuccessCase {

    @Test
    @DisplayName("应该解析 'INGEST_TASK' 为 TASK")
    void shouldParseIngestTask() {
      // Given
      String channel = "INGEST_TASK";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.TASK);
    }

    @Test
    @DisplayName("应该解析 'INGEST_LITERATURE' 为 LITERATURE")
    void shouldParseIngestLiterature() {
      // Given
      String channel = "INGEST_LITERATURE";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.LITERATURE);
    }

    @Test
    @DisplayName("应该解析小写的通道名称")
    void shouldParseLowercaseChannelName() {
      // Given
      String channel = "ingest_task";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.TASK);
    }

    @Test
    @DisplayName("应该解析混合大小写的通道名称")
    void shouldParseMixedCaseChannelName() {
      // Given
      String channel = "InGeSt_TaSk";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.TASK);
    }

    @Test
    @DisplayName("应该修剪前导和尾随空白")
    void shouldTrimWhitespace() {
      // Given
      String channel = "  INGEST_TASK  ";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.TASK);
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
    @DisplayName("旧的三段式通道名称不再支持")
    void shouldReturnEmptyForOldThreePartChannelName() {
      // Given
      String channel = "INGEST_TASK_READY";

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isEmpty(); // 不再支持三段式
    }
  }

  @Nested
  @DisplayName("枚举基本行为")
  class EnumBasicBehavior {

    @Test
    @DisplayName("valueOf 应该正确解析枚举名称")
    void valueOfShouldParseEnumName() {
      // When
      IngestPublishingChannels result = IngestPublishingChannels.valueOf("TASK");

      // Then
      assertThat(result).isEqualTo(IngestPublishingChannels.TASK);
    }

    @Test
    @DisplayName("name() 应该返回枚举常量名称")
    void nameShouldReturnConstantName() {
      // When
      String name = IngestPublishingChannels.TASK.name();

      // Then
      assertThat(name).isEqualTo("TASK");
    }

    @Test
    @DisplayName("ordinal() 应该返回正确的索引")
    void ordinalShouldReturnCorrectIndex() {
      // Then
      assertThat(IngestPublishingChannels.TASK.ordinal()).isZero();
      assertThat(IngestPublishingChannels.LITERATURE.ordinal()).isEqualTo(1);
    }

    @Test
    @DisplayName("toString() 应该返回枚举常量名称")
    void toStringShouldReturnConstantName() {
      // When
      String str = IngestPublishingChannels.TASK.toString();

      // Then
      assertThat(str).isEqualTo("TASK");
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
    @DisplayName("所有枚举值的 channel() 应该包含 domain 和 resource")
    void allChannelsShouldContainAllParts() {
      // When & Then
      for (IngestPublishingChannels channel : IngestPublishingChannels.values()) {
        String channelName = channel.channel();
        assertThat(channelName).contains(channel.domain()).contains(channel.resource());
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
      IngestPublishingChannels channel = IngestPublishingChannels.TASK;

      // Then
      assertThat(channel).isInstanceOf(com.patra.common.messaging.ChannelKey.class);
    }

    @Test
    @DisplayName("channel() 方法应该按 DOMAIN_RESOURCE 格式生成通道名")
    void channelMethodShouldFollowNamingPattern() {
      // Given
      IngestPublishingChannels channel = IngestPublishingChannels.TASK;

      // When
      String expected = channel.domain() + "_" + channel.resource();
      String actual = channel.channel();

      // Then
      assertThat(actual).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("与 channel() 的双向映射一致性")
  class BidirectionalMappingConsistency {

    @Test
    @DisplayName("TASK.channel() 应该能被 fromChannel() 解析回来")
    void taskChannelShouldBeParsableBack() {
      // Given
      String channel = IngestPublishingChannels.TASK.channel();

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.TASK);
    }

    @Test
    @DisplayName("LITERATURE.channel() 应该能被 fromChannel() 解析回来")
    void literatureChannelShouldBeParsableBack() {
      // Given
      String channel = IngestPublishingChannels.LITERATURE.channel();

      // When
      var result = IngestPublishingChannels.fromChannel(channel);

      // Then
      assertThat(result).isPresent().contains(IngestPublishingChannels.LITERATURE);
    }

    @Test
    @DisplayName("所有枚举值都应该支持双向映射")
    void allValuesShouldSupportBidirectionalMapping() {
      // When & Then
      for (IngestPublishingChannels expected : IngestPublishingChannels.values()) {
        String channel = expected.channel();
        var actual = IngestPublishingChannels.fromChannel(channel);
        assertThat(actual).isPresent().contains(expected);
      }
    }
  }
}
