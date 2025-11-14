package com.patra.ingest.infra.messaging.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.patra.ingest.domain.messaging.MessageChannels;
import com.patra.ingest.infra.config.OutboxMqProperties;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RocketMqChannelMapper 单元测试。
 *
 * <p>测试覆盖：
 *
 * <ul>
 *   <li>业务通道到 RocketMQ Topic 的映射
 *   <li>RocketMQ Topic 到业务通道的反向映射
 *   <li>映射存在性检查
 *   <li>未配置通道的异常处理
 *   <li>Topic 前缀支持
 *   <li>自定义映射覆盖默认映射
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@DisplayName("RocketMqChannelMapper 单元测试")
class RocketMqChannelMapperTest {

  private RocketMqChannelMapper channelMapper;
  private OutboxMqProperties properties;

  @BeforeEach
  void setUp() {
    // 创建默认配置（无前缀，无自定义映射）
    properties = mock(OutboxMqProperties.class);
    when(properties.getChannelMapping()).thenReturn(Collections.emptyMap());
    when(properties.getTopicPrefix()).thenReturn("");

    channelMapper = new RocketMqChannelMapper(properties);
  }

  @Nested
  @DisplayName("通道到 Topic 映射测试")
  class ChannelToTopicMappingTests {

    @Test
    @DisplayName("TASK_READY 应映射到 INGEST_TASK_READY")
    void shouldMapTaskReadyChannelToTopic() {
      // When
      String topic = channelMapper.toTopic(MessageChannels.INGEST_TASK_READY);

      // Then
      assertThat(topic).isEqualTo("INGEST_TASK_READY");
    }

    @Test
    @DisplayName("LITERATURE_READY 应映射到 INGEST_LITERATURE_READY")
    void shouldMapLiteratureReadyChannelToTopic() {
      // When
      String topic = channelMapper.toTopic(MessageChannels.INGEST_LITERATURE_READY);

      // Then
      assertThat(topic).isEqualTo("INGEST_LITERATURE_READY");
    }

    @Test
    @DisplayName("未配置的通道应抛出 IllegalArgumentException")
    void shouldThrowExceptionForUnknownChannel() {
      // Given: 未配置的通道
      String unknownChannel = "UNKNOWN_CHANNEL";

      // When & Then
      assertThatThrownBy(() -> channelMapper.toTopic(unknownChannel))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到通道")
          .hasMessageContaining("UNKNOWN_CHANNEL")
          .hasMessageContaining("Topic 映射");
    }

    @Test
    @DisplayName("null 通道应抛出 IllegalArgumentException")
    void shouldThrowExceptionForNullChannel() {
      // When & Then
      assertThatThrownBy(() -> channelMapper.toTopic(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到通道");
    }

    @Test
    @DisplayName("空字符串通道应抛出 IllegalArgumentException")
    void shouldThrowExceptionForEmptyChannel() {
      // When & Then
      assertThatThrownBy(() -> channelMapper.toTopic(""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到通道");
    }
  }

  @Nested
  @DisplayName("Topic 前缀支持测试")
  class TopicPrefixTests {

    @Test
    @DisplayName("配置前缀后应正确添加到 Topic")
    void shouldApplyTopicPrefix() {
      // Given: 配置 "dev-" 前缀
      when(properties.getTopicPrefix()).thenReturn("dev-");
      RocketMqChannelMapper mapperWithPrefix = new RocketMqChannelMapper(properties);

      // When
      String topic = mapperWithPrefix.toTopic(MessageChannels.INGEST_TASK_READY);

      // Then: 应生成 "dev-INGEST_TASK_READY"
      assertThat(topic).isEqualTo("dev-INGEST_TASK_READY");
    }

    @Test
    @DisplayName("空前缀应不影响 Topic")
    void shouldNotApplyEmptyPrefix() {
      // Given: 空前缀
      when(properties.getTopicPrefix()).thenReturn("");
      RocketMqChannelMapper mapperWithEmptyPrefix = new RocketMqChannelMapper(properties);

      // When
      String topic = mapperWithEmptyPrefix.toTopic(MessageChannels.INGEST_TASK_READY);

      // Then: 应生成 "INGEST_TASK_READY"
      assertThat(topic).isEqualTo("INGEST_TASK_READY");
    }

    @Test
    @DisplayName("多环境前缀支持")
    void shouldSupportMultipleEnvironmentPrefixes() {
      // Given: 不同环境的前缀
      String[] prefixes = {"dev-", "test-", "prod-"};

      for (String prefix : prefixes) {
        // When
        when(properties.getTopicPrefix()).thenReturn(prefix);
        RocketMqChannelMapper mapper = new RocketMqChannelMapper(properties);
        String topic = mapper.toTopic(MessageChannels.INGEST_TASK_READY);

        // Then
        assertThat(topic).isEqualTo(prefix + "INGEST_TASK_READY");
      }
    }
  }

  @Nested
  @DisplayName("自定义映射覆盖测试")
  class CustomMappingOverrideTests {

    @Test
    @DisplayName("自定义映射应覆盖默认映射")
    void shouldOverrideDefaultMappingWithCustomMapping() {
      // Given: 自定义映射覆盖 TASK_READY
      when(properties.getChannelMapping())
          .thenReturn(Map.of(MessageChannels.INGEST_TASK_READY, "CUSTOM_TASK_TOPIC"));
      RocketMqChannelMapper customMapper = new RocketMqChannelMapper(properties);

      // When
      String topic = customMapper.toTopic(MessageChannels.INGEST_TASK_READY);

      // Then: 应使用自定义映射
      assertThat(topic).isEqualTo("CUSTOM_TASK_TOPIC");
    }

    @Test
    @DisplayName("自定义映射和默认映射应合并")
    void shouldMergeCustomAndDefaultMappings() {
      // Given: 只覆盖 TASK_READY，保留 LITERATURE_READY 的默认映射
      when(properties.getChannelMapping())
          .thenReturn(Map.of(MessageChannels.INGEST_TASK_READY, "CUSTOM_TASK_TOPIC"));
      RocketMqChannelMapper customMapper = new RocketMqChannelMapper(properties);

      // When
      String customTopic = customMapper.toTopic(MessageChannels.INGEST_TASK_READY);
      String defaultTopic = customMapper.toTopic(MessageChannels.INGEST_LITERATURE_READY);

      // Then
      assertThat(customTopic).isEqualTo("CUSTOM_TASK_TOPIC"); // 自定义映射
      assertThat(defaultTopic).isEqualTo("INGEST_LITERATURE_READY"); // 默认映射
    }

    @Test
    @DisplayName("自定义映射与前缀组合使用")
    void shouldCombineCustomMappingWithPrefix() {
      // Given: 自定义映射 + 前缀
      when(properties.getChannelMapping())
          .thenReturn(Map.of(MessageChannels.INGEST_TASK_READY, "CUSTOM_TOPIC"));
      when(properties.getTopicPrefix()).thenReturn("dev-");
      RocketMqChannelMapper mapper = new RocketMqChannelMapper(properties);

      // When
      String topic = mapper.toTopic(MessageChannels.INGEST_TASK_READY);

      // Then: 应应用自定义映射和前缀
      assertThat(topic).isEqualTo("dev-CUSTOM_TOPIC");
    }
  }

  @Nested
  @DisplayName("Topic 到通道反向映射测试")
  class TopicToChannelReverseMappingTests {

    @Test
    @DisplayName("INGEST_TASK_READY 应反向映射到 TASK_READY")
    void shouldReverseMapIngestTaskReadyTopicToChannel() {
      // When
      String channel = channelMapper.toChannel("INGEST_TASK_READY");

      // Then
      assertThat(channel).isEqualTo(MessageChannels.INGEST_TASK_READY);
    }

    @Test
    @DisplayName("INGEST_LITERATURE_READY 应反向映射到 LITERATURE_READY")
    void shouldReverseMapLiteratureReadyTopicToChannel() {
      // When
      String channel = channelMapper.toChannel("INGEST_LITERATURE_READY");

      // Then
      assertThat(channel).isEqualTo(MessageChannels.INGEST_LITERATURE_READY);
    }

    @Test
    @DisplayName("带前缀的 Topic 应正确反向映射")
    void shouldReverseMapTopicWithPrefix() {
      // Given: 配置前缀
      when(properties.getTopicPrefix()).thenReturn("dev-");
      RocketMqChannelMapper mapperWithPrefix = new RocketMqChannelMapper(properties);

      // When: 反向映射带前缀的 Topic
      String channel = mapperWithPrefix.toChannel("dev-INGEST_TASK_READY");

      // Then: 应去除前缀后映射
      assertThat(channel).isEqualTo(MessageChannels.INGEST_TASK_READY);
    }

    @Test
    @DisplayName("未配置的 Topic 应返回原 Topic")
    void shouldReturnOriginalTopicWhenNoMappingFound() {
      // Given: 未配置映射的 Topic
      String unknownTopic = "UNKNOWN_TOPIC";

      // When
      String result = channelMapper.toChannel(unknownTopic);

      // Then: 返回原 Topic，不抛出异常
      assertThat(result).isEqualTo(unknownTopic);
    }

    @Test
    @DisplayName("null Topic 应返回 null")
    void shouldHandleNullTopic() {
      // When
      String result = channelMapper.toChannel(null);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("空字符串 Topic 应返回空字符串")
    void shouldReturnEmptyStringForEmptyTopic() {
      // When
      String result = channelMapper.toChannel("");

      // Then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("映射存在性检查测试")
  class MappingExistenceCheckTests {

    @Test
    @DisplayName("TASK_READY 应有映射")
    void shouldHaveMappingForTaskReady() {
      // When
      boolean hasMapping = channelMapper.hasMapping(MessageChannels.INGEST_TASK_READY);

      // Then
      assertThat(hasMapping).isTrue();
    }

    @Test
    @DisplayName("LITERATURE_READY 应有映射")
    void shouldHaveMappingForLiteratureReady() {
      // When
      boolean hasMapping = channelMapper.hasMapping(MessageChannels.INGEST_LITERATURE_READY);

      // Then
      assertThat(hasMapping).isTrue();
    }

    @Test
    @DisplayName("未配置的通道应无映射")
    void shouldNotHaveMappingForUnknownChannel() {
      // Given
      String unknownChannel = "UNKNOWN_CHANNEL";

      // When
      boolean hasMapping = channelMapper.hasMapping(unknownChannel);

      // Then
      assertThat(hasMapping).isFalse();
    }

    @Test
    @DisplayName("null 通道应无映射")
    void shouldNotHaveMappingForNullChannel() {
      // When
      boolean hasMapping = channelMapper.hasMapping(null);

      // Then
      assertThat(hasMapping).isFalse();
    }

    @Test
    @DisplayName("空字符串通道应无映射")
    void shouldNotHaveMappingForEmptyChannel() {
      // When
      boolean hasMapping = channelMapper.hasMapping("");

      // Then
      assertThat(hasMapping).isFalse();
    }
  }

  @Nested
  @DisplayName("双向映射一致性测试")
  class BidirectionalMappingConsistencyTests {

    @Test
    @DisplayName("TASK_READY 通道应与 INGEST_TASK_READY Topic 双向映射一致")
    void shouldHaveConsistentBidirectionalMappingForTaskReady() {
      // Given
      String channel = MessageChannels.INGEST_TASK_READY;

      // When: 通道 → Topic → 通道
      String topic = channelMapper.toTopic(channel);
      String reversedChannel = channelMapper.toChannel(topic);

      // Then: 应恢复原通道
      assertThat(reversedChannel).isEqualTo(channel);
    }

    @Test
    @DisplayName("LITERATURE_READY 通道应与 Topic 双向映射一致")
    void shouldHaveConsistentBidirectionalMappingForLiteratureReady() {
      // Given
      String channel = MessageChannels.INGEST_LITERATURE_READY;

      // When: 通道 → Topic → 通道
      String topic = channelMapper.toTopic(channel);
      String reversedChannel = channelMapper.toChannel(topic);

      // Then: 应恢复原通道
      assertThat(reversedChannel).isEqualTo(channel);
    }

    @Test
    @DisplayName("带前缀的双向映射应一致")
    void shouldHaveConsistentBidirectionalMappingWithPrefix() {
      // Given: 配置前缀
      when(properties.getTopicPrefix()).thenReturn("dev-");
      RocketMqChannelMapper mapperWithPrefix = new RocketMqChannelMapper(properties);
      String channel = MessageChannels.INGEST_TASK_READY;

      // When: 通道 → Topic → 通道
      String topic = mapperWithPrefix.toTopic(channel);
      String reversedChannel = mapperWithPrefix.toChannel(topic);

      // Then: 应恢复原通道
      assertThat(topic).isEqualTo("dev-INGEST_TASK_READY");
      assertThat(reversedChannel).isEqualTo(channel);
    }

    @Test
    @DisplayName("所有已配置的通道应有双向映射")
    void shouldHaveBidirectionalMappingForAllConfiguredChannels() {
      // Given: 所有已知通道
      String[] channels = {
        MessageChannels.INGEST_TASK_READY, MessageChannels.INGEST_LITERATURE_READY
      };

      // When & Then: 验证每个通道的双向映射
      for (String channel : channels) {
        String topic = channelMapper.toTopic(channel);
        String reversedChannel = channelMapper.toChannel(topic);

        assertThat(reversedChannel).as("通道 %s 的双向映射应一致", channel).isEqualTo(channel);
      }
    }
  }

  @Nested
  @DisplayName("边界条件测试")
  class BoundaryConditionTests {

    @Test
    @DisplayName("通道名称应大小写敏感")
    void shouldBeCaseSensitiveForChannelNames() {
      // Given: 小写的通道名
      String lowercaseChannel = "task_ready";

      // When & Then: 应抛出异常 (大小写敏感)
      assertThatThrownBy(() -> channelMapper.toTopic(lowercaseChannel))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到通道");
    }

    @Test
    @DisplayName("通道名称应包含空格时抛出异常")
    void shouldThrowExceptionForChannelWithSpaces() {
      // Given: 包含空格的通道名
      String channelWithSpaces = "TASK READY";

      // When & Then
      assertThatThrownBy(() -> channelMapper.toTopic(channelWithSpaces))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("未找到通道");
    }

    @Test
    @DisplayName("Topic 名称应大小写敏感")
    void shouldBeCaseSensitiveForTopicNames() {
      // Given: 小写的 Topic
      String lowercaseTopic = "ingest_task_ready";

      // When
      String result = channelMapper.toChannel(lowercaseTopic);

      // Then: 找不到映射，返回原 Topic
      assertThat(result).isEqualTo(lowercaseTopic);
    }
  }

  @Nested
  @DisplayName("映射规则验证测试")
  class MappingRulesValidationTests {

    @Test
    @DisplayName("服务内通道应映射到 UPPER_CASE 格式 Topic")
    void shouldMapInternalChannelToUpperCaseTopic() {
      // Given: 服务内通道 TASK_READY
      String internalChannel = MessageChannels.INGEST_TASK_READY;

      // When
      String topic = channelMapper.toTopic(internalChannel);

      // Then: 应遵循 UPPER_CASE 命名约定
      assertThat(topic).isEqualTo("INGEST_TASK_READY").matches("[A-Z_]+"); // 全大写 + 下划线
    }

    @Test
    @DisplayName("跨服务通道应映射到 INGEST_{ENTITY}_READY 格式 Topic")
    void shouldMapCrossServiceChannelToUnderscoreSeparatedTopic() {
      // Given: 跨服务通道 LITERATURE_READY
      String crossServiceChannel = MessageChannels.INGEST_LITERATURE_READY;

      // When
      String topic = channelMapper.toTopic(crossServiceChannel);

      // Then: 应遵循 INGEST_{ENTITY}_READY 约定,符合 RocketMQ 规范(仅大写字母+下划线)
      assertThat(topic)
          .isEqualTo("INGEST_LITERATURE_READY")
          .startsWith("INGEST_")
          .matches("[A-Z_]+"); // 全大写 + 下划线
    }

    @Test
    @DisplayName("所有已配置通道的数量应为 2")
    void shouldHaveExactlyTwoConfiguredChannels() {
      // Given: MessageChannels 中定义的所有通道
      String[] expectedChannels = {
        MessageChannels.INGEST_TASK_READY, MessageChannels.INGEST_LITERATURE_READY
      };

      // When & Then: 验证每个通道都有映射
      for (String channel : expectedChannels) {
        assertThat(channelMapper.hasMapping(channel)).as("通道 %s 应有映射", channel).isTrue();
      }

      // 验证只有 2 个通道 (通过尝试访问不存在的通道)
      assertThat(channelMapper.hasMapping("THIRD_CHANNEL")).isFalse();
    }
  }
}
