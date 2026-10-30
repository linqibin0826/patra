package dev.patra.notification.app.inbox.usecase.ingest;

/// 信箱这个消费方：队列名同时是去重记录里的消费方名（`ProcessedEventPort.markProcessed(NAME, eventId)`）。
public final class InboxConsumer {

  /// 队列 `notification.inbox`：绑定 interaction 的评论发表 / 编辑 / 删除、经验删除（PAP-144 加有帮助）。
  public static final String NAME = "notification.inbox";

  /// 只有常量，不实例化。
  private InboxConsumer() {}
}
