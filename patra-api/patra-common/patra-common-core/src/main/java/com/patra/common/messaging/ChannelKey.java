package com.patra.common.messaging;

import java.util.Locale;

/**
 * 消息通道的两部分命名约定契约({@code 领域_资源})。
 *
 * <p><b>设计理念</b>：
 *
 * <ul>
 *   <li><b>粗粒度路由</b>：Channel 代表资源级别的路由标识，而非细粒度的事件类型
 *   <li><b>关注点分离</b>：Channel 负责路由，OperationType 负责业务操作语义
 *   <li><b>RocketMQ 最佳实践</b>：少量 Topic（Channel） + Tags 过滤（OperationType）
 * </ul>
 *
 * <p><b>目标</b>:
 *
 * <ul>
 *   <li>为发布者和消费者提供一致的命名方案
 *   <li>与特定消息传递实现解耦
 *   <li>位于 {@code patra-common} 中，使 API 模块不涉及消息传递细节
 * </ul>
 *
 * <p><b>典型用法</b>:
 *
 * <ul>
 *   <li>发布者在 Domain 模块中实现此接口（例如 {@code IngestPublishingChannels}）
 *   <li>消费者导入已发布的契约以订阅通道
 *   <li>领域模块可能暴露实现此接口的枚举
 * </ul>
 *
 * <p><b>示例</b>：
 *
 * <pre>{@code
 * public enum IngestPublishingChannels implements ChannelKey {
 *   TASK("INGEST", "TASK"),           // channel = "INGEST_TASK"
 *   LITERATURE("INGEST", "LITERATURE"); // channel = "INGEST_LITERATURE"
 *
 *   private final String domain;
 *   private final String resource;
 *
 *   IngestPublishingChannels(String domain, String resource) {
 *     this.domain = domain;
 *     this.resource = resource;
 *   }
 *
 *   @Override
 *   public String domain() { return domain; }
 *
 *   @Override
 *   public String resource() { return resource; }
 * }
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public interface ChannelKey {

  /**
   * 业务领域段（例如 {@code INGEST}、{@code REGISTRY}、{@code ANALYSIS}）。
   *
   * <p>优先使用与服务边界对齐的大写名称。
   *
   * @return 领域名称
   */
  String domain();

  /**
   * 资源或聚合段（例如 {@code TASK}、{@code LITERATURE}、{@code PLAN}）。
   *
   * <p>优先使用与核心聚合或业务对象关联的大写名称。
   *
   * @return 资源名称
   */
  String resource();

  /**
   * 使用下划线构建规范化的大写通道键（例如 {@code INGEST_TASK}、{@code INGEST_LITERATURE}）。
   *
   * <p><b>命名规则</b>：{@code <DOMAIN>_<RESOURCE>}
   *
   * <p><b>与 OperationType 的组合</b>：
   *
   * <ul>
   *   <li>Channel = {@code INGEST_TASK}（资源级别）
   *   <li>OperationType = {@code READY}（操作级别）
   *   <li>RocketMQ Destination = {@code INGEST_TASK:READY}（Topic:Tags 格式）
   * </ul>
   *
   * @return 格式化的通道键（例如 "INGEST_TASK"）
   */
  default String channel() {
    return domain().toUpperCase(Locale.ROOT) + "_" + resource().toUpperCase(Locale.ROOT);
  }
}
