package dev.patra.interaction.api.subject.event;

/// interaction 外送集成事件的交换机（PAP-141）：topic、持久化，路由键是事件类型（各事件 record 的 `ROUTING_KEY`）。
///
/// 发布方与消费方各声明一次（同名同参数，幂等），谁先部署都行。消费方声明绑定时引用这里的常量，不抄字符串。
public final class InteractionExchange {

  /// 交换机名。
  public static final String NAME = "interaction.events";

  /// 只有常量。
  private InteractionExchange() {}
}
