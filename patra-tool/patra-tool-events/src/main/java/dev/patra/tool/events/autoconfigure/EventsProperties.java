package dev.patra.tool.events.autoconfigure;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;

/// 事件模块的配置属性，前缀 `patra.events`。
///
/// @param publisher 发布端
/// @param resubmit 补投
/// @param consumer 消费端
@ConfigurationProperties(prefix = "patra.events")
public record EventsProperties(
    @DefaultValue Publisher publisher,
    @DefaultValue Resubmit resubmit,
    @DefaultValue Consumer consumer) {

  /// 发布端配置。
  ///
  /// @param confirmTimeout 等 broker 确认的上限，超时算发送失败、登记行留给补投
  public record Publisher(@DefaultValue("5s") Duration confirmTimeout) {}

  /// 补投配置。
  ///
  /// @param minAge 只补投发布时间早于这个时长的登记行（刚提交的事件正在异步外送，不去抢）
  public record Resubmit(@DefaultValue("1m") Duration minAge) {}

  /// 消费端配置。
  ///
  /// @param trustedPackages 按消息头 `__TypeId__` 反序列化时信任的包（按包名精确匹配，不含子包）；
  ///     一个队列收多种事件、监听类上用多个 `@RabbitHandler` 时，写发布方 `-api` 模块里集成事件所在的包
  /// @param processedRetention `processed_event` 的保留期：处理时间早于它的行由清理任务删掉；不写单位按天，必须为正；
  ///     要长过同一事件可能再次到达的最长间隔，理由见 spec 4.14
  public record Consumer(
      @DefaultValue List<String> trustedPackages,
      @DefaultValue("30d") @DurationUnit(ChronoUnit.DAYS) Duration processedRetention) {

    /// 防御性拷贝，并校验保留期为正。
    public Consumer {
      trustedPackages = trustedPackages != null ? List.copyOf(trustedPackages) : List.of();
      if (processedRetention == null
          || processedRetention.isZero()
          || processedRetention.isNegative()) {
        throw new IllegalArgumentException(
            "patra.events.consumer.processed-retention 必须为正（不写单位按天），实际 " + processedRetention);
      }
    }
  }
}
