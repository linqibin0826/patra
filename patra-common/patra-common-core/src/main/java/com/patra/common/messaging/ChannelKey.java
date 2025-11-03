package com.patra.common.messaging;

import java.util.Locale;

/**
 * 消息通道的三部分命名约定契约({@code 领域_资源_事件})。
 *
 * <p>目标:
 *
 * <ul>
 *   <li>为发布者和消费者提供一致的命名方案。
 *   <li>与特定消息传递实现解耦。
 *   <li>位于 {@code patra-common} 中,使 API 模块不涉及消息传递细节。
 * </ul>
 *
 * <p><b>典型用法</b>:
 *
 * <ul>
 *   <li>发布者在 API 模块中实现此接口(例如 {@code PublishedChannels})。
 *   <li>消费者导入已发布的契约以订阅通道。
 *   <li>领域模块可能暴露实现此接口的枚举。
 * </ul>
 */
public interface ChannelKey {

  /**
   * 业务领域段(例如 {@code ingest}、{@code registry}、{@code analysis})。
   *
   * <p>优先使用与服务边界对齐的小写名称。
   *
   * @return 领域名称
   */
  String domain();

  /**
   * 资源或聚合段(例如 {@code task}、{@code article}、{@code plan})。
   *
   * <p>优先使用与核心聚合或业务对象关联的小写名称。
   *
   * @return 资源名称
   */
  String resource();

  /**
   * 事件段(例如 {@code ready}、{@code created}、{@code updated})。
   *
   * <p>优先使用描述事实的小写过去式动词。
   *
   * @return 事件名称
   */
  String event();

  /**
   * 使用下划线构建规范化的大写通道键(例如 {@code INGEST_TASK_READY})。
   *
   * @return 格式化的通道键
   */
  default String channel() {
    return domain().toUpperCase(Locale.ROOT)
        + "_"
        + resource().toUpperCase(Locale.ROOT)
        + "_"
        + event().toUpperCase(Locale.ROOT);
  }
}
