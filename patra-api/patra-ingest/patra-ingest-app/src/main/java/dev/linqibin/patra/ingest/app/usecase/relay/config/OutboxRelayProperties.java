package dev.linqibin.patra.ingest.app.usecase.relay.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/// Outbox 中继的默认配置 (可从外部配置文件覆盖)
///
/// 属性前缀: `patra.ingest.outbox-relay.*`
///
/// - `enabled`: 切换中继功能;禁用时调度器返回空报告
///   - `batchSize`: 每批次获取的最大消息数; 调用方侧 `<= 0` 时回退到此处
///   - `leaseDuration`: 每条消息的租约持续时间; 必须覆盖获取 + 发布 + 状态持久化时间
///   - `maxAttempts`: 总允许尝试次数,包括首次执行
///   - `initialBackoff`: 首次重试前的初始延迟
///   - `backoffMultiplier`: 指数退避乘数; 必须大于 `1`
///   - `maxBackoff`: 指数退避的上限,避免无限增长
///
/// 可在线刷新（release spec v0.9 决策 D）：Nacos 配置变更后由 Spring Cloud 原地重新绑定，
/// `RelayPlanBuilder` 每次构建中继计划时重新读取。不合法的值让启动失败；刷新时只会让刷新报错、
/// 健康检查转为 DOWN，新值不会被拦下。
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "patra.ingest.outbox-relay")
public class OutboxRelayProperties {

  /// 是否启用中继功能
  private volatile boolean enabled = true;

  /// 默认批大小
  @Min(1)
  private volatile int batchSize = 200;

  /// 默认租约持续时间
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration leaseDuration = Duration.ofSeconds(30);

  /// 最大尝试次数,包括首次尝试
  @Min(1)
  private volatile int maxAttempts = 5;

  /// 初始退避持续时间
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration initialBackoff = Duration.ofSeconds(5);

  /// 退避乘数
  @DecimalMin(value = "1", inclusive = false)
  private volatile double backoffMultiplier = 2.0d;

  /// 最大退避持续时间
  @NotNull
  @DurationMin(nanos = 1)
  private volatile Duration maxBackoff = Duration.ofMinutes(2);
}
