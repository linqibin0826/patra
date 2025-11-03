package com.patra.ingest.app.usecase.relay.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Outbox 中继的默认配置 (可从外部配置文件覆盖)
 *
 * <p>属性前缀: {@code patra.ingest.outbox-relay.*}
 *
 * <ul>
 *   <li>{@code enabled}: 切换中继功能;禁用时调度器返回空报告
 *   <li>{@code batchSize}: 每批次获取的最大消息数; 调用方侧 {@code <= 0} 时回退到此处
 *   <li>{@code leaseDuration}: 每条消息的租约持续时间; 必须覆盖获取 + 发布 + 状态持久化时间
 *   <li>{@code maxAttempts}: 总允许尝试次数,包括首次执行
 *   <li>{@code initialBackoff}: 首次重试前的初始延迟
 *   <li>{@code backoffMultiplier}: 指数退避乘数; 必须大于 {@code 1}
 *   <li>{@code maxBackoff}: 指数退避的上限,避免无限增长
 * </ul>
 */
@Data
@Component
@ConfigurationProperties(prefix = "patra.ingest.outbox-relay")
public class OutboxRelayProperties {

  /** 是否启用中继功能 */
  private boolean enabled = true;

  /** 默认批大小 */
  private int batchSize = 200;

  /** 默认租约持续时间 */
  private Duration leaseDuration = Duration.ofSeconds(30);

  /** 最大尝试次数,包括首次尝试 */
  private int maxAttempts = 5;

  /** 初始退避持续时间 */
  private Duration initialBackoff = Duration.ofSeconds(5);

  /** 退避乘数 */
  private double backoffMultiplier = 2.0d;

  /** 最大退避持续时间 */
  private Duration maxBackoff = Duration.ofMinutes(2);
}
